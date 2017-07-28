package springexample;

import java.math.BigDecimal;
import java.util.Arrays;

import javax.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.CreditCard;
import com.braintreegateway.Customer;
import com.braintreegateway.MerchantAccount;
import com.braintreegateway.MerchantAccount.FundingDestination;
import com.braintreegateway.MerchantAccountRequest;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.Transaction.Status;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.ValidationError;

@Controller
public class CheckoutController {

	private BraintreeGateway gateway = Application.gateway;

	private Status[] TRANSACTION_SUCCESS_STATUSES = new Status[] {
			Transaction.Status.AUTHORIZED, Transaction.Status.AUTHORIZING,
			Transaction.Status.SETTLED,
			Transaction.Status.SETTLEMENT_CONFIRMED,
			Transaction.Status.SETTLEMENT_PENDING, Transaction.Status.SETTLING,
			Transaction.Status.SUBMITTED_FOR_SETTLEMENT };

	@RequestMapping(value = "/register", method = RequestMethod.GET)
	public String registerMerchant() {
		MerchantAccountRequest request = new MerchantAccountRequest()
				.individual().firstName("Scott").lastName("Powers")
				.email("chandan.benjaram@gmail.com").phone("7068881079")
				.dateOfBirth("1966-03-21").address()
				.streetAddress("6730 Crofton Dr.").locality("Alpharetta")
				.region("GA").postalCode("30005").done().done().business()
				.legalName("KidsLink, Inc.").dbaName("KidsLink, Inc.")
				.taxId("45-5307826").address()
				.streetAddress("3070 Windward Plaza, Ste F-322")
				.locality("Alpharetta").region("GA").postalCode("30005").done()
				.done().funding().descriptor("from www.tingr.org")
				.destination(FundingDestination.BANK)
				.email("chandan.benjaram@gmail.com")
				.accountNumber("3650511177").routingNumber("051400549").done()
				.tosAccepted(true).id("cb1")
				.masterMerchantAccountId("maisasolutionsinc");

		Result<MerchantAccount> result = gateway.merchantAccount().create(
				request);

		return result.getMessage();

	}

	@RequestMapping(value = "/token", method = RequestMethod.GET)
	public String utils(HttpSession session) {
		String clientToken = gateway.clientToken().generate();
		session.setAttribute("data", clientToken);

		return "utils/show";
	}

	@RequestMapping(value = "/sale", method = RequestMethod.GET)
	public String sale(HttpSession session, @RequestParam("nonce") String nonce) {
		System.out.println("NONCE..." + nonce);
		if (nonce == null || nonce.trim().length() <= 0) {
			new ResponseEntity<String>(String.valueOf("missing nonce..."),
					HttpStatus.OK);
		}

		TransactionRequest request = new TransactionRequest()
				.merchantAccountId("cb1").amount(new BigDecimal("10.00"))
				.serviceFeeAmount(new BigDecimal("1.00"))
				.paymentMethodNonce(nonce)
				.customField("facility", "my facility..." + Math.random())
				.customField("season", "testing season..." + Math.random())
				.descriptor().name("REG-FEE-MOSTERSINC").done().options()
				.submitForSettlement(true).holdInEscrow(true).done();

		Result<Transaction> result = gateway.transaction().sale(request);
		Transaction transaction = result.getTransaction();
		String responseStr = result.getMessage()
				+ (result.isSuccess() ? "...success" : "...failed")
				+ "...kind -> " + request.creditCard().getKind();
		if (!result.isSuccess() && transaction != null) {
			StringBuffer str = new StringBuffer();
			str.append("\r\n getTaxAmount..." + transaction.getTaxAmount());
			str.append("\r\n getAuthorizedTransactionId..."
					+ transaction.getAuthorizedTransactionId());
			str.append("\r\n getStatus..." + transaction.getStatus());
			str.append("\r\n getAvsErrorResponseCode..."
					+ transaction.getAvsErrorResponseCode());
			str.append("\r\n getAvsPostalCodeResponseCode..."
					+ transaction.getAvsPostalCodeResponseCode());

			str.append("\r\n getCvvResponseCode..."
					+ transaction.getCvvResponseCode());
			responseStr = str.toString();
		}

		session.setAttribute("data", responseStr);
		return "utils/show";
	}

	@RequestMapping(value = "/checkouts", method = RequestMethod.GET)
	public String checkout(Model model) {
		String clientToken = gateway.clientToken().generate();
		model.addAttribute("clientToken", clientToken);

		return "checkouts/new";
	}

	@RequestMapping(value = "/checkouts", method = RequestMethod.POST)
	public String postForm(@RequestParam("amount") String amount,
			@RequestParam("payment_method_nonce") String nonce, Model model,
			final RedirectAttributes redirectAttributes) {
		BigDecimal decimalAmount;
		try {
			decimalAmount = new BigDecimal(amount);
		} catch (NumberFormatException e) {
			redirectAttributes.addFlashAttribute("errorDetails",
					"Error: 81503: Amount is an invalid format.");
			return "redirect:checkouts";
		}

		TransactionRequest request = new TransactionRequest()
				.amount(decimalAmount).paymentMethodNonce(nonce).options()
				.submitForSettlement(true).done();

		Result<Transaction> result = gateway.transaction().sale(request);

		if (result.isSuccess()) {
			Transaction transaction = result.getTarget();
			return "redirect:checkouts/" + transaction.getId();
		} else if (result.getTransaction() != null) {
			Transaction transaction = result.getTransaction();
			return "redirect:checkouts/" + transaction.getId();
		} else {
			String errorString = "";
			for (ValidationError error : result.getErrors()
					.getAllDeepValidationErrors()) {
				errorString += "Error: " + error.getCode() + ": "
						+ error.getMessage() + "\n";
			}
			redirectAttributes.addFlashAttribute("errorDetails", errorString);
			return "redirect:checkouts";
		}
	}

	@RequestMapping(value = "/checkouts/{transactionId}")
	public String getTransaction(@PathVariable String transactionId, Model model) {
		Transaction transaction;
		CreditCard creditCard;
		Customer customer;

		try {
			transaction = gateway.transaction().find(transactionId);
			creditCard = transaction.getCreditCard();
			customer = transaction.getCustomer();
		} catch (Exception e) {
			System.out.println("Exception: " + e);
			return "redirect:/checkouts";
		}

		model.addAttribute(
				"isSuccess",
				Arrays.asList(TRANSACTION_SUCCESS_STATUSES).contains(
						transaction.getStatus()));
		model.addAttribute("transaction", transaction);
		model.addAttribute("creditCard", creditCard);
		model.addAttribute("customer", customer);

		return "checkouts/show";
	}
}

package service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.io.File;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import springexample.Application;
import springexample.BraintreeGatewayFactory;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@WebAppConfiguration
public class CheckoutControllerTest {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@BeforeClass
	public static void setupConfig() {
		File configFile = new File("config.properties");
		try {
			if (configFile.exists() && !configFile.isDirectory()) {
				Application.gateway = BraintreeGatewayFactory
						.fromConfigFile(configFile);
			} else {
				Application.gateway = BraintreeGatewayFactory
						.fromConfigMapping(System.getenv());
			}
		} catch (NullPointerException e) {
			System.err
					.println("Could not load Braintree configuration from config file or system environment.");
		}
	}

	@Before
	public void setup() throws Exception {
		this.mockMvc = webAppContextSetup(webApplicationContext).build();
	}

	@Test
	public void checkoutReturnsOK() throws Exception {
		mockMvc.perform(get("/checkouts")).andExpect(status().isOk());
	}

	@Test
	public void rendersNewView() throws Exception {
		mockMvc.perform(get("/checkouts"))
				.andExpect(view().name("checkouts/new"))
				.andExpect(model().hasNoErrors())
				.andExpect(model().attributeExists("clientToken"))
				.andExpect(
						xpath(
								"//script[@src='https://js.braintreegateway.com/web/dropin/1.2.0/js/dropin.min.js']")
								.exists());
	}

	@Test
	public void rendersErrorsOnTransactionFailure() throws Exception {
		mockMvc.perform(
				post("/checkouts").param("payment_method_nonce",
						"fake-valid-nonce").param("amount", "2000.00"))
				.andExpect(status().isFound());
	}

	@Test
	public void rendersErrorsOnInvalidAmount() throws Exception {
		mockMvc.perform(
				post("/checkouts").param("payment_method_nonce",
						"fake-valid-nonce").param("amount", "-1.00"))
				.andExpect(status().isFound())
				.andExpect(flash().attributeExists("errorDetails"));

		mockMvc.perform(
				post("/checkouts").param("payment_method_nonce",
						"fake-valid-nonce").param("amount",
						"not_a_valid_amount")).andExpect(status().isFound())
				.andExpect(flash().attributeExists("errorDetails"));
	}

	@Test
	public void redirectsOnTransactionNotFound() throws Exception {
		mockMvc.perform(post("/checkouts/invalid-transaction")).andExpect(
				status().isFound());
	}

	@Test
	public void redirectsRootToNew() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().isFound());
	}
}

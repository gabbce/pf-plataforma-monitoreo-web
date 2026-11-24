package ar.edu.utn.frsf.pfc.management;

import ar.edu.utn.frsf.pfc.management.catalog.DemoFixtureLoader;
import ar.edu.utn.frsf.pfc.management.web.DemoWebAccountLoader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class ManagementApplication {

	public static void main(String[] args) {
		if (args.length > 0 && (args[0].equals("load-demo-fixture") || args[0].equals("load-demo-web-accounts"))) {
			if (args.length != 1) {
				throw new IllegalArgumentException(args[0] + " does not accept arguments");
			}
			try (var context = new SpringApplicationBuilder(ManagementApplication.class)
					.web(WebApplicationType.NONE)
					.run()) {
				if (args[0].equals("load-demo-fixture")) {
					context.getBean(DemoFixtureLoader.class).load();
				} else {
					var environment = context.getEnvironment();
					context.getBean(DemoWebAccountLoader.class).load(
							environment.getRequiredProperty("PFC_DEMO_TECHNICIAN_PASSWORD"),
							environment.getRequiredProperty("PFC_DEMO_OWNER_PASSWORD"),
							environment.getRequiredProperty("PFC_DEMO_UNASSOCIATED_OWNER_PASSWORD"));
				}
			}
			return;
		}
		SpringApplication.run(ManagementApplication.class, args);
	}

}

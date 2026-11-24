package ar.edu.utn.frsf.pfc.management;

import ar.edu.utn.frsf.pfc.management.catalog.DemoFixtureLoader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class ManagementApplication {

	public static void main(String[] args) {
		if (args.length > 0 && args[0].equals("load-demo-fixture")) {
			if (args.length != 1) {
				throw new IllegalArgumentException("load-demo-fixture does not accept arguments");
			}
			try (var context = new SpringApplicationBuilder(ManagementApplication.class)
					.web(WebApplicationType.NONE)
					.run()) {
				context.getBean(DemoFixtureLoader.class).load();
			}
			return;
		}
		SpringApplication.run(ManagementApplication.class, args);
	}

}

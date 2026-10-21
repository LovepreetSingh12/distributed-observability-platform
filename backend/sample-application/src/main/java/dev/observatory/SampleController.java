package dev.observatory;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SampleController {
	private final EventLogger logger;
	private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(getClass());

	public SampleController(EventLogger logger) {
		this.logger = logger;
	}

	@PostMapping("/demo/orders/{number}")
	public Map<String, ?> order(@PathVariable int number) {
		logger.info(
			"ORDER_CREATED",
			1,
			"Order created successfully",
			Map.of(
				"orderId",
				"ORD-" + number,
				"amount",
				100 + Math.floorMod(number * 137, 5000),
				"currency",
				"INR"));
		log.info("Order {} processed", number);
		return logger.counters();
	}

		@PostMapping("/demo/error")
		public Map<String, ?> error() {
			log.error("Payment gateway unavailable", new IllegalStateException("Intentional demo failure"));
			return logger.counters();
		}

	@PostMapping("/demo/invalid")
	public Map<String, ?> invalid() {
		logger.info(
			"ORDER_CREATED", 1, "Invalid payload demonstration", Map.of("amount", "not-a-number"));
		return logger.counters();
	}

	@GetMapping("/demo/counters")
	public Map<String, ?> counters() {
		return logger.counters();
	}
}

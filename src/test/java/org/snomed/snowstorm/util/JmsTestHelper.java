package org.snomed.snowstorm.util;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.stereotype.Component;

/**
 * Helper class for managing JMS broker lifecycle in tests.
 * Ensures the ActiveMQ broker is fully initialized before tests execute.
 */
@Component
@TestComponent
public class JmsTestHelper {

	@Autowired
	private ConnectionFactory connectionFactory;

	/**
	 * Waits for the JMS broker to be ready by attempting to create and close a connection.
	 * Will retry up to 10 times with 500ms delays between attempts.
	 *
	 * @throws JMSException if the broker cannot be reached after all retry attempts
	 * @throws InterruptedException if the thread is interrupted while waiting
	 */
	public void waitForBrokerReady() throws JMSException, InterruptedException {
		int attempts = 0;
		JMSException lastException = null;

		while (attempts < 10) {
			try {
				Connection connection = connectionFactory.createConnection();
				connection.start();
				connection.close();
				return;
			} catch (JMSException e) {
				lastException = e;
				if (attempts++ < 10) {
					Thread.sleep(500);
				}
			}
		}

		if (lastException != null) {
			throw lastException;
		}
	}
}

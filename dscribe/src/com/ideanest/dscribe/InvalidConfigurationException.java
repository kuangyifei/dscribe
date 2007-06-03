package com.ideanest.dscribe;

/**
 * Signals an error in the configuration of an element.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class InvalidConfigurationException extends Exception {

	public InvalidConfigurationException() {
		super();
	}

	public InvalidConfigurationException(String message) {
		super(message);
	}

	public InvalidConfigurationException(Throwable cause) {
		super(cause);
	}

	public InvalidConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

}

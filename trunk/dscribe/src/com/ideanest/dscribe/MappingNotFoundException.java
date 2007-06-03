package com.ideanest.dscribe;

/**
 * Indicates that a tag to class mapping was not found.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class MappingNotFoundException extends Exception {

	public MappingNotFoundException() {
		super();
	}

	public MappingNotFoundException(String message) {
		super(message);
	}

	public MappingNotFoundException(Throwable cause) {
		super(cause);
	}

	public MappingNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

}

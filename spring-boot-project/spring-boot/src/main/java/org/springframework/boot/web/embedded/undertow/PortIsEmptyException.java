package org.springframework.boot.web.embedded.undertow;

public class PortIsEmptyException extends RuntimeException{

	public PortIsEmptyException(String msg){
		super(msg);
	}
}

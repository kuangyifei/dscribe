public class HelloWorld {
	private String msg = "Hello world";
	
	public HelloWorld() {}

	public String sayHello() {
		return msg;
	}

	public static void main(String[] args) {
		System.out.println(new HelloWorld().sayHello());
	}

}

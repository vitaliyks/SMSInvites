public class BadRequest extends Exception{
    public BadRequest(String message){
        super("500 INTERNAL SMS_SERVICE: " + message);
    }
}

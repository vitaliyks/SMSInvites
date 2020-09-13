import com.mysql.cj.protocol.Resultset;
import org.w3c.dom.ls.LSOutput;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.*;

public class main {
    static int countSMS = 0;        // кол-во смс в день
    static boolean latin = true;    // является латиницей

    public static void main(String[] args) throws ClassNotFoundException, SQLException, BadRequest, UnsupportedEncodingException {

        // Номера телефонов для отправки
        String[] phones = {"79995555554", "79000249477", "79995525557", "79995553558"};

        //Сообщение для отправки
        String message = "Для установки приложения пройдите по ссылке example.com";

        // Отправка сообщений
        sendInvites(phones, message);
    }

    public static void sendInvites(String[] phone_numbers, String message) throws BadRequest, ClassNotFoundException, SQLException {

        // Подключение к базе
        String url = "jdbc:mysql://localhost:3306/smscentr?useUnicode=true&serverTimezone=UTC";
        String username = "root";
        String password = "12345678";
        String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
        Class.forName(JDBC_DRIVER);
        try(Connection conn = DriverManager.getConnection(url, username, password);
            Statement statement = conn.createStatement()){

            // --------------- Вызов хранимой процедры (количество отправленных смс за день)------------------
            CallableStatement callableStatement1 = conn.prepareCall("{call getcountinvitations(?,?)}");
            callableStatement1.setInt(1, 4);
            callableStatement1.registerOutParameter(2, Types.INTEGER);
            callableStatement1.execute();
            countSMS = callableStatement1.getInt(2);
//            System.out.println("count SMS today = " + countSMS);
            // -----------------------------------------------------------------------------------------------

            // ------------------------------------- Начало проверок -----------------------------------------
            // 2 Проверка
            if (phone_numbers.length == 0) throw new BadRequest(phone_numbers.getClass() + " : 401 BAD_REQUEST PHONE_NUMBERS_EMPTY: Phone_numbers is missing.");

            // 1 Проверка
            for (int i = 0; i < phone_numbers.length; i++) {
                String s = phone_numbers[i];
                try {
                    long number = Long.parseLong(s);
                    if (s.length() != 11)
                        throw new BadRequest(s.getClass().getName() + " : 400 BAD_REQUEST PHONE_NUMBERS_INVALID: One or several phone numbers do not match with international format.");
                    if (s.contains("+"))
                        throw new BadRequest(s.getClass().getName() + " : 400 BAD_REQUEST PHONE_NUMBERS_INVALID: One or several phone numbers do not match with international format.");
                    char[] arr = s.toCharArray();
                    int a = Integer.parseInt(String.valueOf(arr[0]));
                    if (a != 7)
                        throw new BadRequest(s.getClass().getName() + " : 400 BAD_REQUEST PHONE_NUMBERS_INVALID: One or several phone numbers do not match with international format.");
                } catch (NumberFormatException e) {
                    throw new BadRequest(s.getClass().getName() + " : 400 BAD_REQUEST PHONE_NUMBERS_INVALID: One or several phone numbers do not match with international format.");
                }
            }

            // 3 Проверка
            if (phone_numbers.length > 16) throw new BadRequest(phone_numbers.getClass().getName() + " : 402 BAD_REQUEST PHONE_NUMBERS_INVALID: Too much phone numbers, should be less or equal to 16 per request.");

            // 4 Проверка
            if ((countSMS + phone_numbers.length) > 128) throw new BadRequest("403 BAD_REQUEST PHONE_NUMBERS_INVALID: Too much phone numbers, should be less or equal to 128 per day.");

            // 5 Проверка дублей
            Set<String> set = new HashSet<String>(Arrays.asList(phone_numbers));
            if (set.size() < phone_numbers.length) throw new BadRequest("404 BAD_REQUEST PHONE_NUMBERS_INVALID: Duplicate numbers detected.");

            // 6 Проверка null сообщения
            if (message.isEmpty()) throw new BadRequest("405 BAD_REQUEST MESSAGE_EMPTY: Invite message is missing.");

            // 7 Проверка
            String translit = transliterate(message);
            GSM7 gsm7 = new GSM7();
            String textencode = gsm7.encode(translit);
            String textdecode = gsm7.decode(textencode);
            if (translit.hashCode() != textdecode.hashCode()) throw new BadRequest(message.getClass() + " : 406 BAD_REQUEST MESSAGE_INVALID: Invite message should contain only characters in 7-bit GSM encoding or Cyrillic letters as well");

            // 8 Проверка
            if (latin){     //Длинна сообщения не может быть больше 160 символов для латиницы ...
                if (translit.length() > 160) throw new BadRequest(message.getClass() + " : 407 BAD_REQUEST MESSAGE_INVALID: Invite message too long, should be less or equal to 128 characters of 7-bit GSM charset");
            }
            else{           //  ... и 128 символов для всех остальных.
                if (translit.length() > 128) throw new BadRequest(message.getClass() + " : 407 BAD_REQUEST MESSAGE_INVALID: Invite message too long, should be less or equal to 128 characters of 7-bit GSM charset");
            }
            // ------------------------------------- Конец проверок -----------------------------------------

            // --------------- Вызов хранимой процедры (Отправка приглашений)--------------------------------
            CallableStatement callableStatement2 = conn.prepareCall("{call invite (?,?)}");
            for (int i = 0; i < phone_numbers.length; i++) {    // Перебираем номера телефонов
                boolean sent = true;
                String number = phone_numbers[i];
                CallableStatement callableStatement3 = conn.prepareCall("{call getInvitations()}");
                if (callableStatement3.execute()){
                    ResultSet rs1 = callableStatement3.getResultSet();
                    while (rs1.next()){
                        String phone = rs1.getString("phone");
                        if (phone.equals(number)) {   // Было ли отправлено СМС-приглашение на этот номер?
                            System.out.println("На " + phone + " приглашение уже было отправлено " + rs1.getString("createdon"));
                            sent = false;   // Можно отправлять? Да/Нет
                            break;
                        }
                    }
                }

                // Если все условия выполнены - Отправляем СМС и заносим данные в базу
                if (sent){
                    // -----------------Для отправки смс используем API "SMSЦЕНТР"---------------
//                    SMSCApi sms = new SMSCApi("login", "password", "utf-8", true);
//                    sms.sendSms(number, message, 1, "", "", 0, "", "");
//                    sms.getSmsCost(number, "", 0, 0, "", "");
//                    sms.getBalance();
                    //---------------------------------------------------------------------------

                    callableStatement2.setInt(1, 7);
                    callableStatement2.setString(2, phone_numbers[i]);
                    callableStatement2.execute();
                    System.out.println("На номер " + number + " отправлено СМС-приглашение  \"" + message + "\"");
                }
            }
            // --------------------------------------------------------------------------------
        }
    }

    //--------------------------
    // Транслитерация в латиницу
    public static String transliterate(String text){
        Map<String, String> letters = new HashMap<String, String>();
            letters.put("А", "A");
            letters.put("Б", "B");
            letters.put("В", "V");
            letters.put("Г", "G");
            letters.put("Д", "D");
            letters.put("Е", "E");
            letters.put("Ё", "E");
            letters.put("Ж", "Zh");
            letters.put("З", "Z");
            letters.put("И", "I");
            letters.put("Й", "I");
            letters.put("К", "K");
            letters.put("Л", "L");
            letters.put("М", "M");
            letters.put("Н", "N");
            letters.put("О", "O");
            letters.put("П", "P");
            letters.put("Р", "R");
            letters.put("С", "S");
            letters.put("Т", "T");
            letters.put("У", "U");
            letters.put("Ф", "F");
            letters.put("Х", "Kh");
            letters.put("Ц", "C");
            letters.put("Ч", "Ch");
            letters.put("Ш", "Sh");
            letters.put("Щ", "Sch");
            letters.put("Ъ", "'");
            letters.put("Ы", "Y");
            letters.put("Ь", "'");
            letters.put("Э", "E");
            letters.put("Ю", "Yu");
            letters.put("Я", "Ya");
            letters.put("а", "a");
            letters.put("б", "b");
            letters.put("в", "v");
            letters.put("г", "g");
            letters.put("д", "d");
            letters.put("е", "e");
            letters.put("ё", "e");
            letters.put("ж", "zh");
            letters.put("з", "z");
            letters.put("и", "i");
            letters.put("й", "i");
            letters.put("к", "k");
            letters.put("л", "l");
            letters.put("м", "m");
            letters.put("н", "n");
            letters.put("о", "o");
            letters.put("п", "p");
            letters.put("р", "r");
            letters.put("с", "s");
            letters.put("т", "t");
            letters.put("у", "u");
            letters.put("ф", "f");
            letters.put("х", "h");
            letters.put("ц", "c");
            letters.put("ч", "ch");
            letters.put("ш", "sh");
            letters.put("щ", "sch");
            letters.put("ъ", "'");
            letters.put("ы", "y");
            letters.put("ь", "'");
            letters.put("э", "e");
            letters.put("ю", "yu");
            letters.put("я", "ya");

            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i<text.length(); i++) {
                String l = text.substring(i, i+1);
                if (letters.containsKey(l)) {
                    sb.append(letters.get(l));
                }
                else {
                    sb.append(l);
                    latin = false;
                }
            }
            return sb.toString();
    }
}

// Были использованы следующие хранимые процедуры:
/*
create
    definer = root@localhost procedure getcountinvitations(IN apiid int, OUT cnt int)
BEGIN
    select count(*) into cnt from invitation where createdon = curdate() and api_id = apiid;
END;

create
    definer = root@localhost procedure getInvitations()
BEGIN
    select * from invitation;
END;

create
    definer = root@localhost procedure invite(IN user_id int, IN phones varchar(35))
begin
    INSERT INTO invitation (author, phone, createdon, api_id) VALUES (user_id, phones, curdate(), 4);
end;
 */


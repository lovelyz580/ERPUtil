package wechat.kingdee;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

public class InvokeHelper {
	// K3 Cloud WebSite URL Example "http://192.168.19.113/K3Cloud/"


	/**
	 * 金蝶数据
	 */
//	public static String POST_K3CloudURL = "http://k3.chinajiuan.com:8090/K3Cloud/";
//	public static String dbId = "5ca415480dd2ba";
//	public static String uid = "刘伟涛";
//	public static String pwd = "111111";
//	public static Integer  lang = 2052;

	public static String POST_K3CloudURL =  "http://k3.chinajiuan.com:8090/K3Cloud/";
	public static String dbId ="5ca415480dd2ba";  //5c0dd14da8e5dc
	public static String uid ="֣郑文艳";
	public static String pwd = "000000";
	public static Integer  lang = 2052;



	// Cookie ֵ
	private static String CookieVal = null;

	private static Map map = new HashMap();
	static {
		map.put("Save",
				"Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Save.common.kdsvc");
		map.put("",
				"Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc");
		map.put("Submit",
				"Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Submit.common.kdsvc");
		map.put("Audit",
				"Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Audit.common.kdsvc");
		map.put("UnAudit",
				"Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.UnAudit.common.kdsvc");
		map.put("StatusConvert",
				"Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.StatusConvert.common.kdsvc");
		map.put("chaxun",
				"Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc");
	}

	// HttpURLConnection
	private static HttpURLConnection initUrlConn(String url, JSONArray paras)
			throws Exception {
		URL postUrl = new URL(POST_K3CloudURL.concat(url));
		HttpURLConnection connection = (HttpURLConnection) postUrl
				.openConnection();
		if (CookieVal != null) {
			connection.setRequestProperty("Cookie", CookieVal);
		}
		if (!connection.getDoOutput()) {
			connection.setDoOutput(true);
		}
		connection.setRequestMethod("POST");
		connection.setUseCaches(false);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("Content-Type", "application/json");
		DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());

		UUID uuid = UUID.randomUUID();
		int hashCode = uuid.toString().hashCode();

		JSONObject jObj = new JSONObject();

		jObj.put("format", 1);
		jObj.put("useragent", "ApiClient");
		jObj.put("rid", hashCode);
		jObj.put("parameters", chinaToUnicode(paras.toString()));
		jObj.put("timestamp", new Date().toString());
		jObj.put("v", "1.0");

		out.writeBytes(jObj.toString());
		out.flush();
		out.close();

		return connection;
	}

	// Login
	public static boolean Login(String dbId, String user, String pwd, int lang)
			throws Exception {

		boolean bResult = false;

		String sUrl = "Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc";

		JSONArray jParas = new JSONArray();
		jParas.put(dbId);// ����Id
		jParas.put(user);// �û���
		jParas.put(pwd);// ����
		jParas.put(lang);// ����

		HttpURLConnection connection = initUrlConn(sUrl, jParas);
		// ��ȡCookie
		String key = null;
		for (int i = 1; (key = connection.getHeaderFieldKey(i)) != null; i++) {
			if (key.equalsIgnoreCase("Set-Cookie")) {
				String tempCookieVal = connection.getHeaderField(i);
				if (tempCookieVal.startsWith("kdservice-sessionid")) {
					CookieVal = tempCookieVal;
					break;
				}
			}
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				connection.getInputStream()));
		String line;
		System.out.println(" ============================= ");
		System.out.println(" Contents of post request ");
		System.out.println(" ============================= ");
		while ((line = reader.readLine()) != null) {
			String sResult = new String(line.getBytes(), "utf-8");
			System.out.println(sResult);
			bResult = line.contains("\"LoginResultType\":1");
		}
		System.out.println(" ============================= ");
		System.out.println(" Contents of post request ends ");
		System.out.println(" ============================= ");
		reader.close();

		connection.disconnect();

		return bResult;
	}

	// Save
	public static String Save(String formId, String content) throws Exception {
		return InvokeSave("Save", formId, content);
	}

	// View
	public static void View(String formId, String content) throws Exception {
		Invoke("View", formId, content);
	}

	// Submit
	public static void Submit(String formId, String content) throws Exception {
		Invoke("Submit", formId, content);
	}

	// Audit
	public static void Audit(String formId, String content) throws Exception {
		Invoke("Audit", formId, content);
	}

	// UnAudit
	public static void UnAudit(String formId, String content) throws Exception {
		Invoke("UnAudit", formId, content);
	}

	// StatusConvert
	public static void StatusConvert(String formId, String content)
			throws Exception {
		Invoke("StatusConvert", formId, content);
	}
	// chaxun
	public static String chaxun(String formId, String content)
			throws Exception {
		//Invoke("chaxun", formId, content);
		return Invoke("chaxun", formId, content);
	}
	private static String Invoke(String deal, String formId, String content)
			throws Exception {

		String sUrl = map.get(deal).toString();
		JSONArray jParas = new JSONArray();
		//jParas.put(formId);
		jParas.put(content);
		System.out.println(jParas);
		HttpURLConnection connectionInvoke = initUrlConn(sUrl, jParas);

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				connectionInvoke.getInputStream(),"UTF-8"));

		String line;
		System.out.println(" ============================= ");
		System.out.println(" Contents of post request ");
		System.out.println(" ============================= ");
		String strResult = "";
		while ((line = reader.readLine()) != null) {
			//String sResult = new String(line.getBytes("GBK"), "utf-8");
			String sResult = line;
			//URLDecoder.decode(line, "GBK");
			System.out.println(sResult);
			strResult = sResult;
		}
		System.out.println(" ============================= ");
		System.out.println(" Contents of post request ends ");
		System.out.println(" ============================= ");
		reader.close();

		return strResult;
		//connectionInvoke.disconnect();
	}

	private static String InvokeSave(String deal, String formId, String content)
			throws Exception {

		String sUrl = map.get(deal).toString();
		JSONArray jParas = new JSONArray();
		jParas.put(formId);
		jParas.put(content);
		System.out.println(jParas);
		HttpURLConnection connectionInvoke = initUrlConn(sUrl, jParas);

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				connectionInvoke.getInputStream(),"UTF-8"));

		String line;
		System.out.println(" ============================= ");
		System.out.println(" Contents of post request ");
		System.out.println(" ============================= ");
		String strResult = "";
		while ((line = reader.readLine()) != null) {
			//String sResult = new String(line.getBytes("GBK"), "utf-8");
			String sResult = line;
			//URLDecoder.decode(line, "GBK");
			System.out.println(sResult);
			strResult = sResult;
		}
		System.out.println(" ============================= ");
		System.out.println(" Contents of post request ends ");
		System.out.println(" ============================= ");
		reader.close();

		return strResult;
		//connectionInvoke.disconnect();
	}

	/**
	 * ������ת��Unicode��
	 * 
	 * @param str
	 * @return
	 */
	public static String chinaToUnicode(String str) {
		String result = "";
		for (int i = 0; i < str.length(); i++) {
			int chr1 = (char) str.charAt(i);
			if (chr1 >= 19968 && chr1 <= 171941) {// ���ַ�Χ \u4e00-\u9fa5 (����)
				result += "\\u" + Integer.toHexString(chr1);
			} else {
				result += str.charAt(i);
			}
		}
		return result;
	}
}

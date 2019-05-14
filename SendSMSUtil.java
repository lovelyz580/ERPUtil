package util;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.QuerySendDetailsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.QuerySendDetailsResponse;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;

/**
 * 短信验证码工具类
 * 
 * 短信API产品的DEMO程序，工程中包含了一个SmsDemo类，直接通过
 * 
 * 执行main函数即可体验短信产品API功能(只需要将AK替换成开通了云通信-短信产品功能的AK即可)
 * 
 * 工程依赖了2个jar包(存放在工程的libs目录下) 
 * 1:aliyun-java-sdk-core.jar
 * 2:aliyun-java-sdk-dysmsapi.jar
 *
 * 备注:Demo工程编码采用UTF-8 
 * 国际短信发送请勿参照此DEMO
 * 
 * @author ZY on 2018/09/10
 */

public class SendSMSUtil {

	// 产品名称：云通信短信API产品，开发者无需替换
	private static final String product = "Dysmsapi";
	// 产品域名，开发者无需替换
	private static final String domain = "dysmsapi.aliyuncs.com";

	public static SendSmsResponse sendSms(String phone, String verificationCode) {
		SendSmsResponse sendSmsResponse = null;
		
		try {
			// 可自助调整超时时间
			System.setProperty("sun.net.client.defaultConnectTimeout", "300000");
			System.setProperty("sun.net.client.defaultReadTimeout", "300000");
	
			// 初始化acsClient，暂不支持region化
			IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", Config.SMS_KEYID, Config.SMS_KEYSECRET);
			DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", product, domain);
			IAcsClient acsClient = new DefaultAcsClient(profile);
	
			// 组装请求对象-具体描述见控制台-文档部分内容
			SendSmsRequest smsRequest = new SendSmsRequest();
			// 必填：待发送手机号码
			smsRequest.setPhoneNumbers(phone);
			// 必填：短信签名
			smsRequest.setSignName(Config.SMS_SIGNNAME);
			// 必填：短信模板
			smsRequest.setTemplateCode(Config.SMS_TEMPLATECODE);
			// 可选：模板中的变量替换JSON串
			// 如：模板内容为"您的验证码${code}，该验证码5分钟内有效，请勿泄漏于他人！"时，此处的值为
			smsRequest.setTemplateParam("{\"code\":\"" + verificationCode + "\"}");
						
			// 发送验证码
			// 提示：此处可能会抛出异常，注意catch
			sendSmsResponse = acsClient.getAcsResponse(smsRequest);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return sendSmsResponse;
	}

	//忘记密码时发送的验证码
	public static SendSmsResponse sendSmsforGetPassword(String phone, String verificationCode) {
		SendSmsResponse sendSmsResponse = null;

		try {
			// 可自助调整超时时间
			System.setProperty("sun.net.client.defaultConnectTimeout", "300000");
			System.setProperty("sun.net.client.defaultReadTimeout", "300000");

			// 初始化acsClient，暂不支持region化
			IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", Config.SMS_KEYID, Config.SMS_KEYSECRET);
			DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", product, domain);
			IAcsClient acsClient = new DefaultAcsClient(profile);

			// 组装请求对象-具体描述见控制台-文档部分内容
			SendSmsRequest smsRequest = new SendSmsRequest();
			// 必填：待发送手机号码
			smsRequest.setPhoneNumbers(phone);
			// 必填：短信签名
			smsRequest.setSignName(Config.SMS_SIGNNAME);
			// 必填：短信模板
			smsRequest.setTemplateCode(Config.SMS_TEMPLATECODEFORGETPASSWORD);
			// 可选：模板中的变量替换JSON串
			// 如：模板内容为"您的验证码${code}，该验证码5分钟内有效，请勿泄漏于他人！"时，此处的值为
			smsRequest.setTemplateParam("{\"code\":\"" + verificationCode + "\"}");

			// 发送验证码
			// 提示：此处可能会抛出异常，注意catch
			sendSmsResponse = acsClient.getAcsResponse(smsRequest);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return sendSmsResponse;
	}

	public static QuerySendDetailsResponse querySendDetails(String bizId, String phone) throws ClientException {
		QuerySendDetailsResponse querySendDetailsResponse = null;
		
		try {
			// 可自助调整超时时间
			System.setProperty("sun.net.client.defaultConnectTimeout", "300000");
			System.setProperty("sun.net.client.defaultReadTimeout", "300000");
	
			// 初始化acsClient,暂不支持region化
			IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", Config.SMS_KEYID, Config.SMS_KEYSECRET);
			DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", product, domain);
			IAcsClient acsClient = new DefaultAcsClient(profile);
	
			// 组装请求对象
			QuerySendDetailsRequest request = new QuerySendDetailsRequest();
			// 必填：手机号码
			request.setPhoneNumber(phone);
			// 可选：流水号
			request.setBizId(bizId);
			// 必填：发送日期 支持30天内记录查询，格式yyyyMMdd
			SimpleDateFormat ft = new SimpleDateFormat("yyyyMMdd");
			request.setSendDate(ft.format(new Date()));
			// 必填：页大小
			request.setPageSize(10L);
			// 必填：当前页码从1开始计数
			request.setCurrentPage(1L);
	
			// 发送
			// 提示：此处可能会抛出异常，注意catch
			querySendDetailsResponse = acsClient.getAcsResponse(request);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return querySendDetailsResponse;
	}

//	public static void main(String[] args) throws ClientException, InterruptedException {
//		// 生成6位随机数
//		String random = String.valueOf((Math.random() * 1000000)).substring(0, 6);
//		if (random.indexOf(".") != -1) {
//			random = random.replace(".", "0"); // 将随机数中的.转换为0
//		}
//		
//		// 发短信
//		SendSmsResponse response = sendSms("13784982133", "\"" + random + "\"");
//		System.out.println("短信接口返回的数据----------------");
//		System.out.println("Code=" + response.getCode());
//		System.out.println("Message=" + response.getMessage());
//		System.out.println("RequestId=" + response.getRequestId());
//		System.out.println("BizId=" + response.getBizId());
//
//		Thread.sleep(3000L);
//
//		// 查明细
//		if (response.getCode() != null && response.getCode().equals("OK")) {
//			QuerySendDetailsResponse querySendDetailsResponse = querySendDetails(response.getBizId(), "13784982133");
//			System.out.println("短信明细查询接口返回数据----------------");
//			System.out.println("Code=" + querySendDetailsResponse.getCode());
//			System.out.println("Message=" + querySendDetailsResponse.getMessage());
//			
//			int i = 0;
//			for (QuerySendDetailsResponse.SmsSendDetailDTO smsSendDetailDTO : querySendDetailsResponse.getSmsSendDetailDTOs()) {
//				System.out.println("SmsSendDetailDTO[" + i + "]:");
//				System.out.println("Content=" + smsSendDetailDTO.getContent());
//				System.out.println("ErrCode=" + smsSendDetailDTO.getErrCode());
//				System.out.println("OutId=" + smsSendDetailDTO.getOutId());
//				System.out.println("PhoneNum=" + smsSendDetailDTO.getPhoneNum());
//				System.out.println("ReceiveDate=" + smsSendDetailDTO.getReceiveDate());
//				System.out.println("SendDate=" + smsSendDetailDTO.getSendDate());
//				System.out.println("SendStatus=" + smsSendDetailDTO.getSendStatus());
//				System.out.println("Template=" + smsSendDetailDTO.getTemplateCode());
//			}
//			
//			System.out.println("TotalCount=" + querySendDetailsResponse.getTotalCount());
//			System.out.println("RequestId=" + querySendDetailsResponse.getRequestId());
//		}
//	}
	
}

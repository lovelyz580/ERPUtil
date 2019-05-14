package wechat.kingdee;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import sun.LayuiDataTemplet;
import wechat.entity.KingDeeEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 金蝶数据
 *
 * Restful
 *
 * @author ZY on 2019/03/19
 */

@Controller
@RequestMapping("/kingdee")
public class InvokeTest {

	/**
	 * 金蝶数据
	 *
	 * @param kingDeeEntity
	 * @return
	 *
	 * @author ZY on 2018/09/21
	 */
	@ResponseBody
	@RequestMapping("/selectByKingdee")
	public LayuiDataTemplet<KingDeeEntity> updateByUser (@RequestBody KingDeeEntity kingDeeEntity) throws Exception {

		LayuiDataTemplet<KingDeeEntity> returnData = new LayuiDataTemplet<KingDeeEntity>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		//输入名称3个字以上才搜索，
		if (kingDeeEntity.getKingdeename().length() < 3) {
			returnData.setMsg("请输入3个字查询！");
			return returnData;
		}

		InvokeHelper.POST_K3CloudURL = "http://k3.chinajiuan.com:8090/K3Cloud/";
		String dbId = "5c0dd14da8e5dc";
		String uid = "֣郑文艳";
		String pwd = "000000";
		int lang = 2052;

		List<KingDeeEntity> list = new ArrayList<KingDeeEntity>();
		if (InvokeHelper.Login(dbId, uid, pwd, lang)) {

			// ���۶����������
			// ҵ�����Id
			String sFormId = "kaf3fb720cd15419b9edd236973808a76";
			//��Ҫ���������
			// �����ֶο�����Ҫ�����Լ�ʵ��ֵ���޸�
			// FCustId FSalerId FMaterialId FUnitID
			// 查询项目名称
			String sContent = "{\"FormId\":\"kaf3fb720cd15419b9edd236973808a76\",\"TopRowCount\":0,\"Limit\":5,\"StartRow\":0,\"FilterString\":\"FName like '%"+ kingDeeEntity.getKingdeename() +"%' and FUseOrgId='103337' \",\"OrderString\":\"FID ASC\",\"FieldKeys\":\"FName,fid,fnumber\"}";
			//String sContent = "{\\\"Creator\\\":\\\"\\\",\\\"NeedUpDateFields\\\":[],\\\"NeedReturnFields\\\":[],\\\"IsDeleteEntry\\\":\\\"True\\\",\\\"SubSystemId\\\":\\\"\\\",\\\"IsVerifyBaseDataField\\\":\\\"false\\\",\\\"IsEntryBatchFill\\\":\\\"True\\\",\\\"Model\\\":{\\\"FID\\\":\\\"0\\\",\\\"FNumber\\\":\\\"\\\",\\\"FName\\\":\\\"SAF\\\",\\\"FDescription\\\":\\\"\\\",\\\"FCreateOrgId\\\":{\\\"FNumber\\\":\\\"100\\\"},\\\"FUseOrgId\\\":{\\\"FNumber\\\":\\\"100\\\"},\\\"F_PAEZ_Base\\\":{\\\"FNumber\\\":\\\"CUST0049\\\"},\\\"F_PAEZ_Amount\\\":0.0,\\\"F_PAEZ_Base1\\\":{\\\"FNumber\\\":\\\"PRE001\\\"},\\\"F_PAEZ_Assistant1\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant2\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Text\\\":\\\"NoneNoneNoneSAF\\\",\\\"F_PAEZ_Text1\\\":\\\"\\\",\\\"F_PAEZ_UserId\\\":{\\\"FUserID\\\":\\\"\\\"},\\\"F_PAEZ_Date\\\":\\\"1900-01-01\\\",\\\"F_PAEZ_Assistant3\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Group\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant\\\":{\\\"FNumber\\\":\\\"China\\\"},\\\"F_PAEZ_Assistant4\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant5\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant6\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant7\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Base2\\\":{\\\"FNumber\\\":\\\"10004\\\"},\\\"F_PAEZ_Text3\\\":\\\"\\\",\\\"F_PAEZ_Text4\\\":\\\"ASDF\\\"}}";
			//InvokeHelper.chaxun(sFormId, sContent);

			String str = InvokeHelper.chaxun(sFormId, sContent);
			String[] s1 = str.replaceAll("],", "]%%").split("%%");
			String[][] arr = new String[s1.length][];
			for(int i=0;i<arr.length;i++) { String[] s2 = s1[i].split(",");
				arr[i] = new String[s2.length];
				for(int j=0;j<s2.length;j++) {
					arr[i][j] = s2[j].replaceAll("\\[|\\]", "");
				} }


				if (arr.length > 1) {
					for (int i = 0; i < arr.length; i++) {
						//for (int j = 0; j < arr[i].length; j++) {
						//二维数组中只有2个参数
						KingDeeEntity kingDeeEntityList = new KingDeeEntity();
						String kingdeename = arr[i][0].replace("\"", "");
						kingDeeEntityList.setKingdeename(kingdeename);
						kingDeeEntityList.setKingdeeorderid(Integer.parseInt(arr[i][1]));
						String kingdeeorderfnumber = arr[i][2].replace("\"", "");
						kingDeeEntityList.setKingdeeorderfnumber(kingdeeorderfnumber);
						list.add(kingDeeEntityList);
						//list.add(arr[i][j]);
						// }
					}
				}
//			 str = str.substring(1, str.length());//去掉第一个字符[
//			str = str.substring(0,str.length() - 1);
//			String[] arr = str.split("],"); // 用,分割
			//List<KingDeeEntity> list= JsonUtil.jsonToList(InvokeHelper.chaxun(sFormId, sContent), KingDeeEntity.class);
			System.out.println("执行成功 success");
		}

		returnData.setData(list); // 数据List，默认为null
		return returnData;
	}


//	public static void main(String[] args) throws Exception {
//		InvokeHelper.POST_K3CloudURL = "http://k3.chinajiuan.com:8090/K3Cloud/";
//		String dbId = "5c0dd14da8e5dc";
//		String uid = "֣郑文艳";
//		String pwd = "000000";
//		int lang = 2052;
//
//		if (InvokeHelper.Login(dbId, uid, pwd, lang)) {
//
//			// ���۶����������
//			// ҵ�����Id
//			String sFormId = "kaf3fb720cd15419b9edd236973808a76";
//			//��Ҫ���������
//			// �����ֶο�����Ҫ�����Լ�ʵ��ֵ���޸�
//			// FCustId FSalerId FMaterialId FUnitID
//			// 查询项目名称
//			String sContent = "{\"FormId\":\"kaf3fb720cd15419b9edd236973808a76\",\"TopRowCount\":0,\"Limit\":20,\"StartRow\":0,\"FilterString\":\"FName like '%河北%' and FUseOrgId='103337' \",\"OrderString\":\"FID ASC\",\"FieldKeys\":\"FName,fid\"}";
//			//String sContent = "{\\\"Creator\\\":\\\"\\\",\\\"NeedUpDateFields\\\":[],\\\"NeedReturnFields\\\":[],\\\"IsDeleteEntry\\\":\\\"True\\\",\\\"SubSystemId\\\":\\\"\\\",\\\"IsVerifyBaseDataField\\\":\\\"false\\\",\\\"IsEntryBatchFill\\\":\\\"True\\\",\\\"Model\\\":{\\\"FID\\\":\\\"0\\\",\\\"FNumber\\\":\\\"\\\",\\\"FName\\\":\\\"SAF\\\",\\\"FDescription\\\":\\\"\\\",\\\"FCreateOrgId\\\":{\\\"FNumber\\\":\\\"100\\\"},\\\"FUseOrgId\\\":{\\\"FNumber\\\":\\\"100\\\"},\\\"F_PAEZ_Base\\\":{\\\"FNumber\\\":\\\"CUST0049\\\"},\\\"F_PAEZ_Amount\\\":0.0,\\\"F_PAEZ_Base1\\\":{\\\"FNumber\\\":\\\"PRE001\\\"},\\\"F_PAEZ_Assistant1\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant2\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Text\\\":\\\"NoneNoneNoneSAF\\\",\\\"F_PAEZ_Text1\\\":\\\"\\\",\\\"F_PAEZ_UserId\\\":{\\\"FUserID\\\":\\\"\\\"},\\\"F_PAEZ_Date\\\":\\\"1900-01-01\\\",\\\"F_PAEZ_Assistant3\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Group\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant\\\":{\\\"FNumber\\\":\\\"China\\\"},\\\"F_PAEZ_Assistant4\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant5\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant6\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Assistant7\\\":{\\\"FNumber\\\":\\\"\\\"},\\\"F_PAEZ_Base2\\\":{\\\"FNumber\\\":\\\"10004\\\"},\\\"F_PAEZ_Text3\\\":\\\"\\\",\\\"F_PAEZ_Text4\\\":\\\"ASDF\\\"}}";
//			InvokeHelper.chaxun(sFormId, sContent);
//
//			System.out.println("执行成功 success");
//		}
//	}
}

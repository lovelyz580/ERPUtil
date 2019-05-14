package sun.bz1.restful;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;

import sun.LayuiDataTemplet;
import sun.bz1.entity.*;
import sun.bz1.service.*;
import sun.token.TokenEntity;
import sun.token.TokenManager;
import util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 用户表
 * 
 * Restful
 * 
 * @author WJF on 2018/09/03
 */

@Controller
@RequestMapping("/user")
public class F_UserRestful {

	@Autowired
	private UserService userService;

	@Autowired
	private AreaService areaService;
	@Autowired
	private WechatService wechatService;
	
	@Autowired
	private CreditUserServiceInitialSetupService userServiceSetupService;
	
	@Autowired
	private CreditUserCustomerInitialSetupService userCustomerSetupService;
	
	@Autowired
	private TokenManager redisTokenManager;

	@Autowired
	private LogService logService;

	@Autowired
	private EvaluateServiceService evaluateServiceService;

	@Autowired
	private EvaluateCustomerService evaluateCustomerService;

	@Autowired
	private GoodsService goodsService;
	
	private SendSmsResponse response;
	
	/**
	 * 添加用户数据
	 * 
	 * 如果用户角色为维修工(WX)、安装队(AZ)，则添加用户表、用户_维修工、安装队表、信用值_变化表
	 * 
	 * 如果用户角色为客户(KH)，则添加用户表、用户_客户表、信用值_变化表
	 * 
	 * 版本信息存放在用户表的实体类中
	 * 
	 * @param models
	 * @return
	 * 
	 * @author WJF on 2018/09/06
	 */
	@ResponseBody
	@RequestMapping("/insertTwoTables")
	public LayuiDataTemplet<User> insertTwoTables(@RequestBody Map<String, Object> models) {
		LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null
		
		// JSON转换为实体类
		User user = null;
		UserServiceEntity userServiceEntity = null;
		UserCustomer userCustomer = null;
		try {
			user = JsonUtil.json2obj((String)models.get("user"), User.class);
			userServiceEntity = JsonUtil.json2obj((String)models.get("userService"), UserServiceEntity.class);
			userCustomer = JsonUtil.json2obj((String)models.get("userCustomer"), UserCustomer.class);
		} catch (Exception e1) {
			e1.printStackTrace();
			
			returnData.setMsg("JSON转换为实体类发生异常！");
			return returnData;
		} 
		
		if (user != null) {
			// 版本号不为空，则验证版本号
			try {
				if (user.getVersion() != null && !user.getVersion().isEmpty()) {
					// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
					int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
					if (compare < 0) {
						returnData.setMsg("版本较低，请更新版本！");
						return returnData;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				
				returnData.setMsg("版本比较发生异常！");
				return returnData;
			}

			// 用户表
			// 添加数据
			if (user.getUserid() == null || user.getUserid().isEmpty()) {
				user.setUserid(Config.SIGN_USER + UUID.randomUUID().toString()); // 用户ID(YHBG+UUID)
				user.setUsercreatetime(new Date()); // 用户创建时间
			}
			user.setUserstate("BJ"); // 用户状态(BJ:编辑/TJ:提交/PZ:批准...)
			user.setUsermoney(0.0); // 用户余额
//			user.setUserupdateuserid(user.getUsercreateuserid()); // 用户更新人ID(YHBG+UUID)
//			user.setUserupdatetime(new Date()); // 用户更新时间
			
			// 信用值_变化表
			CreditChange creditChange = new CreditChange();
			// 添加数据
			creditChange.setCreditchangeid(Config.SIGN_CREDITCHANGE + UUID.randomUUID().toString()); // 信用值变化ID(XYBH+UUID)
			creditChange.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)
			creditChange.setCreditchangetype("CSZ"); // 信用值变化类型信用值变化类型(CSZ:初始值/WXTHDD:维修人员退回订单/KHQXQR:客户取消订单/KHYSHG:客户验收合格/KHGHWX:客户更换维修人员/KHWTPD:客户委托平台派单/PLWX:客户评论维修人员/PLKH:维修人员评论客户)
			creditChange.setCreditchangecreatetime(new Date()); // 信用值变化创建时间
			
			// 添加
			int count = 0;
			//4个角色之前客户跟其他分开时的代码
			/*if ("WX,".equals(user.getUserrole()) || "AZ,".equals(user.getUserrole()) ||
					"WX,AZ,".equals(user.getUserrole()) || "AZ,WX,".equals(user.getUserrole())) {*/
			//一个人拥有客户角色可以和其他角色共存的代码
			if (user.getUserrole().contains("WX,") || user.getUserrole().contains("AZ,") ||
					user.getUserrole().contains("JZ,")) {
				// 添加用户表和用户_维修工、安装队表
				if (userServiceEntity == null) {
					// 传递的维修工、安装队数据错误
					returnData.setMsg("传递的维修工、安装队数据错误！");
					return returnData;
				} else {
					// 用户表
					// 添加数据
					double credit = 0; // 信用值
					
					// 查询初始值
					CreditUserServiceInitialSetup setup = userServiceSetupService.selectBySetup(new CreditUserServiceInitialSetup()).get(0); 
					
					// 初始值_用户真实姓名
					if (user.getUserrealname() != null && !user.getUserrealname().isEmpty()) {
						credit += setup.getCredituserservicerealname();
					}
					// 初始值_用户性别
					if (user.getUsersex() != null && !user.getUsersex().isEmpty()) {
						credit += setup.getCredituserservicesex();
					}
					// 初始值_用户手机号码
					if (user.getUserphone() != null && !user.getUserphone().isEmpty()) {
						credit += setup.getCredituserservicephone();
					}
					// 初始值_用户身份证号码
					if (user.getUsercodeid() != null && !user.getUsercodeid().isEmpty()) {
						credit += setup.getCredituserservicecodeid();
					}
					// 初始值_用户邮箱
					if (user.getUseremail() != null && !user.getUseremail().isEmpty()) {
						credit += setup.getCredituserserviceemail();
					}
					// 初始值_用户接单区域
					if (userServiceEntity.getAreaid() != null && !userServiceEntity.getAreaid().isEmpty()) {
						credit += setup.getCredituserservicearea();
					}
					// 初始值_用户接单规模
					if (userServiceEntity.getScaleid() != null && !userServiceEntity.getScaleid().isEmpty()) {
						credit += setup.getCredituserservicescale();
					}
					// 初始值_用户接单状态(Y:接单/N:不接单)
					if (userServiceEntity.getUserservicestate() != null && !userServiceEntity.getUserservicestate().isEmpty()) {
						credit += setup.getCredituserservicestate();
					}
					
					// 为用户添加初始值
					user.setUsercredit(credit);
					
					// 用户_维修工、安装队表
					// 添加数据
					userServiceEntity.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)
					
					// 信用值_变化表
					// 添加数据
					creditChange.setCreditchange(credit); // 信用值变化值
					
					// 添加用户表、用户_维修工、安装队表、信用值_变化表
					count = userService.insertUserAndUserServiceAndCreditChange(user, userServiceEntity, creditChange); // 添加
				}
			}
			if (user.getUserrole().contains("KH,")) {
				// 添加用户表和用户_客户表
				if (userCustomer == null) {
					// 传递的客户数据错误
					returnData.setMsg("传递的客户数据错误！");
					return returnData;
				} else {
					// 用户表
					// 添加数据
					double credit = 0; // 信用值
					
					// 查询初始值
					CreditUserCustomerInitialSetup setup = userCustomerSetupService.selectBySetup(new CreditUserCustomerInitialSetup()).get(0); 
					
					// 初始值_用户真实姓名
					if (user.getUserrealname() != null && !user.getUserrealname().isEmpty()) {
						credit += setup.getCreditusercustomerrealname();
					}
					// 初始值_用户性别
					if (user.getUsersex() != null && !user.getUsersex().isEmpty()) {
						credit += setup.getCreditusercustomersex();
					}
					// 初始值_用户手机号码
					if (user.getUserphone() != null && !user.getUserphone().isEmpty()) {
						credit += setup.getCreditusercustomerphone();
					}
					// 初始值_用户身份证号码
					if (user.getUsercodeid() != null && !user.getUsercodeid().isEmpty()) {
						credit += setup.getCreditusercustomercodeid();
					}
					// 初始值_用户邮箱
					if (user.getUseremail() != null && !user.getUseremail().isEmpty()) {
						credit += setup.getCreditusercustomeremail();
					}
					// 初始值_用户城市
					if (userCustomer.getUsercustomercity() != null && !userCustomer.getUsercustomercity().isEmpty()) {
						credit += setup.getCreditusercustomercity();
					}
					// 初始值_用户单位
					if (userCustomer.getUsercustomercompany() != null && !userCustomer.getUsercustomercompany().isEmpty()) {
						credit += setup.getCreditusercustomercompany();
					}
					// 初始值_用户所属组织
					if (userCustomer.getUsercustomerorganization() != null && !userCustomer.getUsercustomerorganization().isEmpty()) {
						credit += setup.getCreditusercustomerorganization();
					}
					// 初始值_用户地址
					if (userCustomer.getUsercustomeraddress() != null && !userCustomer.getUsercustomeraddress().isEmpty()) {
						credit += setup.getCreditusercustomeraddress();
					}
					
					// 为用户添加初始值
					user.setUsercredit(credit);
					
					// 用户_客户表
					// 添加数据
					userCustomer.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)
					
					// 信用值_变化表
					// 添加数据
					creditChange.setCreditchange(credit); // 信用值变化值
								
					// 添加用户表、用户_客户表、信用值_变化表
					count = userService.insertUserAndUserCustomerAndCreditChange(user, userCustomer, creditChange); // 添加
				}
			} /*else {
				// 传递的用户角色数据错误
				returnData.setMsg("传递的用户角色数据错误！");
				return returnData;
			}*/
			
			// 返回数据
			if (count == 0) {
				returnData.setMsg("添加失败！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("添加成功！");
			}
		} else {
			// 传递的用户数据错误
			returnData.setMsg("传递的用户数据错误！");
		}

		return returnData;
	}
	
	/**
	 * 添加用户数据
	 * 
	 * 微信小程序完善用户时使用到
	 * 
	 * 如果用户角色为维修工(WX)、安装队(AZ)，则添加用户表、用户_维修工、安装队表、信用值_变化表
	 * 
	 * 如果用户角色为客户(KH)，则添加用户表、用户_客户表、信用值_变化表
	 * 
	 * 版本信息存放在用户表的实体类中
	 * 
	 * @param models
	 * @return
	 * 
	 * @author ZY on 2018/09/18
	 */
	@ResponseBody
	@RequestMapping("/updateTwoTablesByPerfect")
	public LayuiDataTemplet<User> updateTwoTablesByPerfect(@RequestBody Map<String, Object> models) {
		LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null
		
		// JSON转换为实体类
		User user = null;
		UserServiceEntity userServiceEntity = null;
		UserCustomer userCustomer = null;
		try {
			user = JsonUtil.json2obj((String)models.get("user"), User.class);
			userServiceEntity = JsonUtil.json2obj((String)models.get("userService"), UserServiceEntity.class);
			userCustomer = JsonUtil.json2obj((String)models.get("userCustomer"), UserCustomer.class);
		} catch (Exception e1) {
			e1.printStackTrace();
			
			returnData.setMsg("JSON转换为实体类发生异常！");
			return returnData;
		} 
		
		if (user != null) {
			// 版本号不为空，则验证版本号
			try {
				if (user.getVersion() != null && !user.getVersion().isEmpty()) {
					// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
					int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
					if (compare < 0) {
						returnData.setMsg("版本较低，请更新版本！");
						return returnData;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				
				returnData.setMsg("版本比较发生异常！");
				return returnData;
			}

			// 用户表
			// 添加数据
			if (user.getUserid() == null || user.getUserid().isEmpty()) {
				user.setUserid(Config.SIGN_USER + UUID.randomUUID().toString()); // 用户ID(YHBG+UUID)
				user.setUsercreatetime(new Date()); // 用户创建时间
			}
			user.setUserstate("PZ"); // 用户状态(BJ:编辑/TJ:提交/PZ:批准...)
			user.setUsermoney(0.0); // 用户余额
//			user.setUserupdateuserid(user.getUsercreateuserid()); // 用户更新人ID(YHBG+UUID)
//			user.setUserupdatetime(new Date()); // 用户更新时间
			
			// 信用值_变化表
			CreditChange creditChange = new CreditChange();
			// 添加数据
			creditChange.setCreditchangeid(Config.SIGN_CREDITCHANGE + UUID.randomUUID().toString()); // 信用值变化ID(XYBH+UUID)
			creditChange.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)
			creditChange.setCreditchangetype("CSZ"); // 信用值变化类型信用值变化类型(CSZ:初始值/WXTHDD:维修人员退回订单/KHQXQR:客户取消订单/KHYSHG:客户验收合格/KHGHWX:客户更换维修人员/KHWTPD:客户委托平台派单/PLWX:客户评论维修人员/PLKH:维修人员评论客户)
			creditChange.setCreditchangecreatetime(new Date()); // 信用值变化创建时间
			
			// 添加
			int count = 0;
			//4个角色之前的代码不能跟客户同时存在
			/*if ("WX,".equals(user.getUserrole()) || "AZ,".equals(user.getUserrole()) ||
					"WX,AZ,".equals(user.getUserrole()) || "AZ,WX,".equals(user.getUserrole())) {*/
			if (user.getUserrole().contains("WX,") || user.getUserrole().contains("AZ,") ||
					user.getUserrole().contains("JZ,") ) {
				// 添加用户表和用户_维修工、安装队表
				if (userServiceEntity == null) {
					// 传递的维修工、安装队数据错误
					returnData.setMsg("传递的维修工、安装队数据错误！");
					return returnData;
				} else {
					// 用户表
					// 添加数据
					double credit = 70; // 信用值
					
					/*// 查询初始值
					CreditUserServiceInitialSetup setup = userServiceSetupService.selectBySetup(new CreditUserServiceInitialSetup()).get(0); 
					
					// 初始值_用户真实姓名
					if (user.getUserrealname() != null && !user.getUserrealname().isEmpty()) {
						credit += setup.getCredituserservicerealname();
					}
					// 初始值_用户性别
					if (user.getUsersex() != null && !user.getUsersex().isEmpty()) {
						credit += setup.getCredituserservicesex();
					}
					// 初始值_用户手机号码
					if (user.getUserphone() != null && !user.getUserphone().isEmpty()) {
						credit += setup.getCredituserservicephone();
					}
					// 初始值_用户身份证号码
					if (user.getUsercodeid() != null && !user.getUsercodeid().isEmpty()) {
						credit += setup.getCredituserservicecodeid();
					}
					// 初始值_用户邮箱
					if (user.getUseremail() != null && !user.getUseremail().isEmpty()) {
						credit += setup.getCredituserserviceemail();
					}
					// 初始值_用户接单区域
					if (userServiceEntity.getAreaid() != null && !userServiceEntity.getAreaid().isEmpty()) {
						credit += setup.getCredituserservicearea();
					}
					// 初始值_用户接单规模
					if (userServiceEntity.getScaleid() != null && !userServiceEntity.getScaleid().isEmpty()) {
						credit += setup.getCredituserservicescale();
					}
					// 初始值_用户接单状态(Y:接单/N:不接单)
					if (userServiceEntity.getUserservicestate() != null && !userServiceEntity.getUserservicestate().isEmpty()) {
						credit += setup.getCredituserservicestate();
					}*/
					
					// 为用户添加初始值
					user.setUsercredit(credit);
					
					// 用户_维修工、安装队表
					// 添加数据
					userServiceEntity.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)
					/*String areaCode = "";
					String areaIds = userServiceEntity.getAreaid();
					String[] areaidList=areaIds.split(",");
					for(int i=0;i<areaidList.length;i++){
						Area area = new Area();
						area.setPagenumber(-1);//不分页
						area.setAreaid(areaidList[i]);//区域ID
						List<Area> areaList = null;
						areaList = areaService.selectByArea(area);
						if (areaList.size() == 0) {
							returnData.setMsg("未找到相应的区域信息！");

							return returnData;
						}
						if (areaList.get(0).getAreaprovincecode().equals("156")){
							Area areaAll = new Area();
							areaAll.setPagenumber(-1);
							List<Area> areaListAll = null;
							areaListAll = areaService.selectByArea(areaAll);
							for (int j=0; j<areaListAll.size(); j++){
								areaCode = areaCode +  areaListAll.get(j).getAreaprovincecode() + ",";
							}

						} else {
							if (areaList.get(0).getAreacitycode().equals("156")) {
								Area areaAllProvince = new Area();
								areaAllProvince.setPagenumber(-1);
								areaAllProvince.setAreacitycode(areaList.get(0).getAreaprovincecode());
								List<Area> areaListAllProvince = null;
								areaListAllProvince = areaService.selectByArea(areaAllProvince);
								if (areaListAllProvince.size() == 0){
									areaCode = areaCode + areaList.get(0).getAreaprovincecode() + ",";
								}else {
									areaCode = areaCode + areaList.get(0).getAreaprovincecode() + ",";
									for (int k = 0; k < areaListAllProvince.size(); k++) {
										areaCode = areaCode + areaListAllProvince.get(k).getAreaprovincecode() + ",";
									}
								}
							}else if (!areaList.get(0).getAreacitycode().equals("156")) {
								areaCode = areaCode + areaList.get(0).getAreaprovincecode() + ",";
							}
						}
					}
					userServiceEntity.setAreacode(areaCode); // 接单区域代码*/
					
					// 信用值_变化表
					// 添加数据
					creditChange.setCreditchange(credit); // 信用值变化值
					
					// 添加用户表、用户_维修工、安装队表、信用值_变化表
					count = userService.updateUserAndUserServiceAndCreditChange(user, userServiceEntity, creditChange); // 添加
				}
			}
			if (user.getUserrole().contains("KH,")) {
				// 添加用户表和用户_客户表
				if (userCustomer == null) {
					// 传递的客户数据错误
					returnData.setMsg("传递的客户数据错误！");
					return returnData;
				} else {
					// 用户表
					// 添加数据
					double credit = 70; // 信用值
					
					/*// 查询初始值
					CreditUserCustomerInitialSetup setup = userCustomerSetupService.selectBySetup(new CreditUserCustomerInitialSetup()).get(0); 
					
					// 初始值_用户真实姓名
					if (user.getUserrealname() != null && !user.getUserrealname().isEmpty()) {
						credit += setup.getCreditusercustomerrealname();
					}
					// 初始值_用户性别
					if (user.getUsersex() != null && !user.getUsersex().isEmpty()) {
						credit += setup.getCreditusercustomersex();
					}
					// 初始值_用户手机号码
					if (user.getUserphone() != null && !user.getUserphone().isEmpty()) {
						credit += setup.getCreditusercustomerphone();
					}
					// 初始值_用户身份证号码
					if (user.getUsercodeid() != null && !user.getUsercodeid().isEmpty()) {
						credit += setup.getCreditusercustomercodeid();
					}
					// 初始值_用户邮箱
					if (user.getUseremail() != null && !user.getUseremail().isEmpty()) {
						credit += setup.getCreditusercustomeremail();
					}
					// 初始值_用户城市
					if (userCustomer.getUsercustomercity() != null && !userCustomer.getUsercustomercity().isEmpty()) {
						credit += setup.getCreditusercustomercity();
					}
					// 初始值_用户单位
					if (userCustomer.getUsercustomercompany() != null && !userCustomer.getUsercustomercompany().isEmpty()) {
						credit += setup.getCreditusercustomercompany();
					}
					// 初始值_用户所属组织
					if (userCustomer.getUsercustomerorganization() != null && !userCustomer.getUsercustomerorganization().isEmpty()) {
						credit += setup.getCreditusercustomerorganization();
					}
					// 初始值_用户地址
					if (userCustomer.getUsercustomeraddress() != null && !userCustomer.getUsercustomeraddress().isEmpty()) {
						credit += setup.getCreditusercustomeraddress();
					}*/
					
					// 为用户添加初始值
					user.setUsercredit(credit);
					
					// 用户_客户表
					// 添加数据
					userCustomer.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)
					
					// 信用值_变化表
					// 添加数据
					creditChange.setCreditchange(credit); // 信用值变化值
								
					// 更新用户表、用户_客户表、信用值_变化表
					count = userService.updateUserAndUserCustomerAndCreditChange(user, userCustomer, creditChange); // 更新
				}
			} /*else {
				// 传递的用户角色数据错误
				returnData.setMsg("传递的用户角色数据错误！");
				return returnData;
			}*/
			
			// 返回数据
			if (count == 0) {
				returnData.setMsg("添加失败！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("添加成功！");
			}
		} else {
			// 传递的用户数据错误
			returnData.setMsg("传递的用户数据错误！");
		}

		return returnData;
	}

	/**
	 * 更新用户数据
	 * 
	 * @param user
	 * @return
	 * 
	 * @author WJF on 2018/09/21
	 */
	@ResponseBody
	@RequestMapping("/updateByUser")
	public LayuiDataTemplet<User> updateByUser(@RequestBody User user) {
		LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null
		
		// 版本号不为空，则验证版本号
		try {
			if (user.getVersion() != null && !user.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 更新判断
		if (user.getId() == null || user.getId() == 0) {
			if (user.getUserid() == null || user.getUserid().isEmpty()) {
				returnData.setMsg("缺少更新条件，更新失败！");
				return returnData;
			}
		}
		
		// 更新数据
		// 查询是否存在这个手机号码
		if (user.getUserphone() != null && !user.getUserphone().isEmpty()) {
			UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
			selectUser.setUserphone(user.getUserphone()); // 用户手机号码
			selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
			List<UserAndUserServiceAndUserCustomer> userList = null;
			userList = userService.selectThreeTablesByUnionData(selectUser); // 查询
			if (userList != null && userList.size() != 0) {
				returnData.setMsg("该手机号码已注册，请更换其他手机号码！");
				return returnData;
			}
		}
		user.setUserupdatetime(new Date()); // 用户更新时间
		
		// 更新
		int count = 0;
		count = userService.updateByUser(user); // 更新
				
		// 返回数据
		if (count == 0) {
			returnData.setMsg("更新失败！");
		} else {
			returnData.setCount(count);
			returnData.setMsg("更新成功！");
		}

		return returnData;
	}
	
	/**
	 * 更新用户数据
	 * 
	 * 如果用户角色为维修工(WX)、安装队(AZ)，则更新用户表和用户_维修工、安装队表
	 * 
	 * 如果用户角色为客户(KH)，则更新用户表和用户_客户表
	 * 
	 * 版本信息存放在用户表的实体类中
	 * 
	 * @param models
	 * @return
	 * 
	 * @author WJF on 2018/09/06
	 */
	@ResponseBody
	@RequestMapping("/updateTwoTables")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> updateTwoTables(@RequestBody Map<String, Object> models) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null
		
		// JSON转换为实体类
		User user = null;
		UserServiceEntity userServiceEntity = null;
		UserCustomer userCustomer = null;
		try {
			user = JsonUtil.json2obj((String)models.get("user"), User.class);
			userServiceEntity = JsonUtil.json2obj((String)models.get("userService"), UserServiceEntity.class);
			userCustomer = JsonUtil.json2obj((String)models.get("userCustomer"), UserCustomer.class);
		} catch (Exception e1) {
			e1.printStackTrace();
					
			returnData.setMsg("JSON转换为实体类发生异常！");
			return returnData;
		} 
		
		// 版本号不为空，则验证版本号
		try {
			if (user.getVersion() != null && !user.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 用户表更新数据
		// 查询是否存在这个手机号码
		if (user.getUserphone() != null && !user.getUserphone().isEmpty()) {
			UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
			selectUser.setUserphone(user.getUserphone()); // 用户手机号码
			selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
			List<UserAndUserServiceAndUserCustomer> userList = null;
			userList = userService.selectThreeTablesByUnionData(selectUser); // 查询
			/*if (userList != null && userList.size() != 0) {
				returnData.setMsg("该手机号码已注册，请更换其他手机号码！");
				return returnData;
			}*/
		}
		user.setUserupdatetime(new Date()); // 用户更新时间
		
		// 更新
		int count = 0;
		//客户不能和其他角色共存
		/*if ("WX,".equals(user.getUserrole()) || "AZ,".equals(user.getUserrole()) ||
				"WX,AZ,".equals(user.getUserrole()) || "AZ,WX,".equals(user.getUserrole())) {*/
		if (user.getUserrole().contains("WX,") || user.getUserrole().contains("AZ,") ||
				user.getUserrole().contains("JZ,") ) {
			// 更新用户表和用户_维修工、安装队表
			if (userServiceEntity == null) {
				// 传递的维修工、安装队数据错误
				returnData.setMsg("传递的维修工、安装队数据错误！");
				return returnData;
			} else {
				// 更新数据
				userServiceEntity.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)
				/*String areaCode = "";
				String areaIds = userServiceEntity.getAreaid();
				String[] areaidList=areaIds.split(",");
				for(int i=0;i<areaidList.length;i++){
					Area area = new Area();
					area.setPagenumber(-1);//不分页
					area.setAreaid(areaidList[i]);//区域ID
					List<Area> areaList = null;
					areaList = areaService.selectByArea(area);
					if (areaList.size() == 0) {
						returnData.setMsg("未找到相应的区域信息！");

						return returnData;
					}
					if (areaList.get(0).getAreaprovincecode().equals("156")){
						Area areaAll = new Area();
						areaAll.setPagenumber(-1);
						List<Area> areaListAll = null;
						areaListAll = areaService.selectByArea(areaAll);
						for (int j=0; j<areaListAll.size(); j++){
							areaCode = areaCode +  areaListAll.get(j).getAreaprovincecode() + ",";
						}

					} else {
						if (areaList.get(0).getAreacitycode().equals("156")) {
							Area areaAllProvince = new Area();
							areaAllProvince.setPagenumber(-1);
							areaAllProvince.setAreacitycode(areaList.get(0).getAreaprovincecode());
							List<Area> areaListAllProvince = null;
							areaListAllProvince = areaService.selectByArea(areaAllProvince);
							if (areaListAllProvince.size() == 0){
								areaCode = areaCode + areaList.get(0).getAreaprovincecode() + ",";
							}else {
								areaCode = areaCode + areaList.get(0).getAreaprovincecode() + ",";
								for (int k = 0; k < areaListAllProvince.size(); k++) {
									areaCode = areaCode + areaListAllProvince.get(k).getAreaprovincecode() + ",";
								}
							}
						}else if (!areaList.get(0).getAreacitycode().equals("156")) {
							areaCode = areaCode + areaList.get(0).getAreaprovincecode() + ",";
						}
					}
				}
				userServiceEntity.setAreacode(areaCode); // 接单区域代码*/
						
				// 更新用户表和用户_维修工、安装队表
				count = userService.updateUserAndUserService(user, userServiceEntity); // 更新
			}
		}
		if (user.getUserrole().contains("KH,")) {
			// 更新用户表和用户_客户表
			if (userCustomer == null) {
				// 传递的客户数据错误
				returnData.setMsg("传递的客户数据错误！");
				return returnData;
			} else {
				// 更新数据
				userCustomer.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)
									
				// 更新用户表和用户_客户表
				count = userService.updateUserAndUserCustomer(user, userCustomer); // 更新
			}
		}
		/*{
			// 传递的用户角色数据错误
			returnData.setMsg("传递的用户角色数据错误！");
			return returnData;
		}*/
				
		// 返回数据
		if (count == 0) {
			returnData.setMsg("更新失败！");
		} else {
			UserAndUserServiceAndUserCustomer selectUser2 = new UserAndUserServiceAndUserCustomer();
			selectUser2.setUserid(user.getUserid()); // 用户id
			selectUser2.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
			List<UserAndUserServiceAndUserCustomer> userList2 = null;
			userList2 = userService.selectThreeTablesByUnionData(selectUser2); // 查询
			returnData.setData(userList2);
			returnData.setCount(count);
			returnData.setMsg("更新成功！");
		}

		return returnData;
	}

	/**
	 * 更新用户数据 (areacode)
	 *
	 *
	 * 版本信息存放在用户表的实体类中
	 *
	 * @param models
	 * @return
	 *
	 * @author ZY on 2018/10/31
	 */
	@ResponseBody
	@RequestMapping("/updateTwoTablesAreaCode")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> updateTwoTablesAreaCode(@RequestBody Map<String, Object> models) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// JSON转换为实体类
		User user = null;
		UserServiceEntity userServiceEntity = null;
		UserCustomer userCustomer = null;
		try {
			user = JsonUtil.json2obj((String)models.get("user"), User.class);
			userServiceEntity = JsonUtil.json2obj((String)models.get("userService"), UserServiceEntity.class);
			userCustomer = JsonUtil.json2obj((String)models.get("userCustomer"), UserCustomer.class);
		} catch (Exception e1) {
			e1.printStackTrace();

			returnData.setMsg("JSON转换为实体类发生异常！");
			return returnData;
		}

		// 版本号不为空，则验证版本号
		try {
			if (user.getVersion() != null && !user.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 用户表更新数据
		// 查询是否存在这个手机号码
		if (user.getUserphone() != null && !user.getUserphone().isEmpty()) {
			UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
			selectUser.setUserphone(user.getUserphone()); // 用户手机号码
			selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
			List<UserAndUserServiceAndUserCustomer> userList = null;
			userList = userService.selectThreeTablesByUnionData(selectUser); // 查询
			/*if (userList != null && userList.size() != 0) {
				returnData.setMsg("该手机号码已注册，请更换其他手机号码！");
				return returnData;
			}*/
		}
		user.setUserupdatetime(new Date()); // 用户更新时间

		// 更新
		int count = 0;
			// 更新用户表和用户_维修工、安装队表
			if (userServiceEntity == null) {
				// 传递的维修工、安装队数据错误
				returnData.setMsg("传递的维修工、安装队数据错误！");
				return returnData;
			} else {
				// 更新数据
				userServiceEntity.setUserid(user.getUserid()); // 用户ID(YHBG+UUID)

				// 更新用户表和用户_维修工、安装队表
				count = userService.updateUserAndUserService(user, userServiceEntity); // 更新
			}

		// 返回数据
		if (count == 0) {
			returnData.setMsg("更新失败！");
		} else {
			UserAndUserServiceAndUserCustomer selectUser2 = new UserAndUserServiceAndUserCustomer();
			selectUser2.setUserid(user.getUserid()); // 用户id
			selectUser2.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
			List<UserAndUserServiceAndUserCustomer> userList2 = null;
			userList2 = userService.selectThreeTablesByUnionData(selectUser2); // 查询
			returnData.setData(userList2);
			returnData.setCount(count);
			returnData.setMsg("更新成功！");
		}

		return returnData;
	}

	
	/**
	 * 根据UserAndUserServiceAndUserCustomer实体联表查询
	 * 
	 * 可以进行分页查询
	 * 
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 * 
	 * pageSize 每页的数据量
	 * 
	 * @param unionData
	 * @return
	 * 
	 * @author WJF on 2018/09/06
	 */
	@ResponseBody
	@RequestMapping("/selectThreeTablesByUnionData")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> selectThreeTablesByUnionData(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null
		
		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}
				
				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			} 
			
			// 查询数量
			int count = 0;
			count = userService.selectThreeTablesCountByUnionData(unionData); // 查询数量
			
			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("查询成功！");
				List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesByUnionData(unionData); // 查询数据
				returnData.setData(userList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}
	
	/**
	 * 根据UserAndUserServiceAndUserCustomer实体联表模糊查询
	 * 
	 * 可以进行分页查询
	 * 
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 * 
	 * pageSize 每页的数据量
	 * 
	 * @param unionData
	 * @return
	 * 
	 * @author WJF on 2018/09/06
	 */
	@ResponseBody
	@RequestMapping("/selectThreeTablesBySelectData")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> selectThreeTablesBySelectData(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null
		
		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}
				
				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			} 
			
			// 查询数量
			int count = 0;
			count = userService.selectThreeTablesCountBySelectData(unionData); // 查询数量
			
			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("查询成功！");
				List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectData(unionData); // 查询数据
                for (int i = 0; i < userList.size(); i++) {
                    if (null != userList.get(i).getUserinvitationuserid() && !userList.get(i).getUserinvitationuserid().equals("")) {
                        UserAndUserServiceAndUserCustomer userAndUserServiceAndUserCustomer = new UserAndUserServiceAndUserCustomer();
                        userAndUserServiceAndUserCustomer.setPagenumber(-1);//不分页
                        userAndUserServiceAndUserCustomer.setUserid(userList.get(i).getUserinvitationuserid());//推荐人ID
                        List<UserAndUserServiceAndUserCustomer> invitationUserList = userService.selectThreeTablesByUnionData(userAndUserServiceAndUserCustomer);
                        if (invitationUserList.size() > 0){
                            userList.get(i).setUserinvitationusername(invitationUserList.get(0).getUserrealname());
                        }
                    }
                }
				returnData.setData(userList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}
	
	/**
   	 * 客户金额变化列表
   	 * 
   	 * 微信小程序查询用户金额变化时用到
   	 * 
   	 * 根据UserAndUserServiceAndUserCustomer实体联表查询
   	 * 
   	 * 可以进行分页查询
	 * 
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 * 
	 * pageSize 每页的数据量
   	 * 
   	 * @param unionData
   	 * @return
   	 * 
   	 * @author WJF on 2018/09/20
   	 */
	@ResponseBody
	@RequestMapping("/wechatSelectCustomerMoneyListByUnionData")
	public LayuiDataTemplet<WechatUserMoney> wechatSelectCustomerMoneyListByUnionData(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<WechatUserMoney> returnData = new LayuiDataTemplet<WechatUserMoney>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null
		
		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}
				
				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			} 
			
			// 查询数量
			int count = 0;
			count = userService.wechatSelectCustomerMoneyListCountByUnionData(unionData); // 查询数量
			
			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("查询成功！");
				List<WechatUserMoney> moneyList = userService.wechatSelectCustomerMoneyListByUnionData(unionData); // 查询数据
				returnData.setData(moneyList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}
	
	/**
   	 * 维修工、安装队金额变化列表
   	 * 
   	 * 微信小程序查询用户金额变化时用到
   	 * 
   	 * 根据UserAndUserServiceAndUserCustomer实体联表查询
   	 * 
   	 * 可以进行分页查询
	 * 
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 * 
	 * pageSize 每页的数据量
   	 * 
   	 * @param unionData
   	 * @return
   	 * 
   	 * @author WJF on 2018/09/20
   	 */
	@ResponseBody
	@RequestMapping("/wechatSelectServiceMoneyListByUnionData")
	public LayuiDataTemplet<WechatUserMoney> wechatSelectServiceMoneyListByUnionData(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<WechatUserMoney> returnData = new LayuiDataTemplet<WechatUserMoney>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null
		
		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}
				
				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			} 
			
			// 查询数量
			int count = 0;
			count = userService.wechatSelectServiceMoneyListCountByUnionData(unionData); // 查询数量
			
			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("查询成功！");
				List<WechatUserMoney> moneyList = userService.wechatSelectServiceMoneyListByUnionData(unionData); // 查询数据
				returnData.setData(moneyList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}

    /**
     * 更新用户状态(批量修改)
     *
     * @param user
     * @return
     *
     * @author ZY on 2018/12/11
     */
    @ResponseBody
    @RequestMapping("/updateByUserState")
    public LayuiDataTemplet<User> updateByUserState(@RequestBody User user) {
        LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
        returnData.setCode(0); // 默认为0
        returnData.setCount(0); // 数据的数量，默认为0
        returnData.setData(null); // 数据List，默认为null

        // 版本号不为空，则验证版本号
        try {
            if (user.getVersion() != null && !user.getVersion().isEmpty()) {
                // 前者大则返回一个正数，后者大返回一个负数，相等则返回0
                int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
                if (compare < 0) {
                    returnData.setMsg("版本较低，请更新版本！");
                    return returnData;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

            returnData.setMsg("版本比较发生异常！");
            return returnData;
        }


        // 更新数据
        user.setUserupdatetime(new Date()); // 用户更新时间

        // 更新
        int count = 0;
        count = userService.updateByUserState(user); // 更新

        // 返回数据
        if (count == 0) {
            returnData.setMsg("更新失败！");
        } else {
            returnData.setCount(count);
            returnData.setMsg("更新成功！");
        }

        return returnData;
    }

	/**
	 * 根据 用户ID List 删除信息
   	 * 
	 * @param user
	 * @return
	 * 
	 * @author WJF on 2018/09/06
	 */
	@ResponseBody
	@RequestMapping("/deleteThreeTablesByUserIdList")
	public LayuiDataTemplet<User> deleteThreeTablesByUserIdList(@RequestBody User user) {
		LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (user.getVersion() != null && !user.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 删除
		int count = 0;
		count = userService.deleteThreeTablesByUserIdList(user.getUuidlist());
	
		// 返回数据
		if (count == 0) {
			returnData.setMsg("删除失败！");
		} else {
			returnData.setCount(count);
			returnData.setMsg("删除成功！");
		}

		return returnData;
	}

	/**
	 * 忘记密码时获取验证码
	 *
	 * @param unionData
	 * @return
	 *
	 * @author ZY on 2018/11/28
	 */
	@ResponseBody
	@RequestMapping("/verificationCodeUpdatePassword")
	public LayuiDataTemplet<User> verificationCodeUpdatePassword(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}


		try {
			if (unionData.getUserphone() == null || unionData.getUserphone().isEmpty()) {
				returnData.setMsg("请输入手机号码！");
				return returnData;
			}

			// 生成6位随机数
			String random = String.valueOf((Math.random() * 1000000)).substring(0, 6);
			if (random.indexOf(".") != -1) {
				random = random.replace(".", "0"); // 将随机数中的.转换为0
			}

			// 查询是否存在这个手机号码是否能登录通过手机号和密码
			UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
			selectUser.setUserphone(unionData.getUserphone());
			selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
			List<UserAndUserServiceAndUserCustomer> userList = new ArrayList<UserAndUserServiceAndUserCustomer>();
			userList = userService.selectThreeTablesByUnionData(selectUser); // 查询

			int count = 0;
			if (userList.size() == 0) {
				returnData.setMsg("该手机还未注册，请注册！");
				return returnData;
			} else if (userList.size() > 0 && userList.get(0).getUserpassword() == null && userList.get(0).getUserpassword().isEmpty()){
				returnData.setMsg("该手机还未注册，请注册！");
				return returnData;
			} else {
				// 根据手机号更新
				User updateUser = new User();
				updateUser.setUserphone(unionData.getUserphone()); // 用户手机号码
				updateUser.setUsersms(random); // 用户短信验证码
				updateUser.setUsersmstime(new Date()); //用户短信验证码创建时间

				count = userService.updateByUserPhone(updateUser); // 更新
			}


			response = SendSMSUtil.sendSmsforGetPassword(unionData.getUserphone(), random);

			if (response.getCode() != null && response.getCode().equals("OK")) {

				returnData.setCount(count);
				returnData.setMsg("短信发送成功，请注意查收！");
			} else {
				returnData.setMsg("短信发送失败，" + response.getCode());
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("发送短信失败！");
			return returnData;
		}

		return returnData;
	}

	/**
	 * 忘记密码修改密码
	 *
	 * @param unionData
	 * @return
	 *
	 * @author WJF on 2018/09/10
	 */
	@ResponseBody
	@RequestMapping("/forGetUpdatePassword")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> forGetUpdatePassword(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 注册
		int count = 0;

		// 查询是否存在这个手机号码
		UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
		selectUser.setUserphone(unionData.getUserphone());
		selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
		List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesByUnionData(selectUser); // 查询


		// 通过手机号码查询验证码、验证码创建时间
		UserAndUserServiceAndUserCustomer userAndUserServiceAndUserCustomer = userList.get(0);

		// 验证码时间差计算
		Long sendTime = userAndUserServiceAndUserCustomer.getUsersmstime().getTime(); // 验证码创建时间
		Long nowTime = new Date().getTime(); // 当前时间
		// 时间差
		Long time = nowTime - sendTime;

		// 验证
		if (!unionData.getUsersms().equals(userAndUserServiceAndUserCustomer.getUsersms())) {
			returnData.setMsg("请输入正确的验证码!");
			return returnData;
		} else if (time > 5 * 60 * 1000) {
			// 5分钟
			returnData.setMsg("验证码超时，请重新获取！");
			return returnData;
		} else {
			// 更新
			User user = new User();
			user.setUserid(userAndUserServiceAndUserCustomer.getUserid());
			user.setUserpassword(unionData.getUserpassword());

			count = userService.updateByUser(user);
		}

		// 返回数据
		if (count == 0) {
			returnData.setMsg("修改密码失败！");
		} else {
			returnData.setCount(count);
			returnData.setData(userList);
			returnData.setMsg("修改密码成功！");
		}

		return returnData;
	}

	/**
	 * 获取验证码
	 * 
	 * @param unionData
	 * @return
	 * 
	 * @author ZY on 2018/09/10
	 */
	@ResponseBody
	@RequestMapping("/verificationCode")
	public LayuiDataTemplet<User> verificationCode(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 获取验证码
//		// 查询是否存在这个手机号码
//		UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
//		selectUser.setUserphone(unionData.getUserphone());
//		List<UserAndUserServiceAndUserCustomer> userList = new ArrayList<UserAndUserServiceAndUserCustomer>();
//		userList = userService.selectThreeTablesByUnionData(selectUser); // 查询
//		if (userList.size() != 0) {
//			returnData.setMsg("该手机号码已注册，请更换其他手机号码！");
//			return returnData;
//		}
		
		try {
			if (unionData.getUserphone() == null || unionData.getUserphone().isEmpty()) {
				returnData.setMsg("请输入手机号码！");
				return returnData;
			}
			
			// 生成6位随机数
	    	String random = String.valueOf((Math.random() * 1000000)).substring(0, 6);
	    	if (random.indexOf(".") != -1) {
	    		random = random.replace(".", "0"); // 将随机数中的.转换为0
	    	}
	    	
			response = SendSMSUtil.sendSms(unionData.getUserphone(), random);
		
			if (response.getCode() != null && response.getCode().equals("OK")) {
				// 查询是否存在这个手机号码
				UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
				selectUser.setUserphone(unionData.getUserphone());
				selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
				List<UserAndUserServiceAndUserCustomer> userList = new ArrayList<UserAndUserServiceAndUserCustomer>();
				userList = userService.selectThreeTablesByUnionData(selectUser); // 查询
				
				if (userList.size() == 0) {
					// 添加
					User insertUser = new User();
					insertUser.setUserid(Config.SIGN_USER + UUID.randomUUID().toString()); // 用户表UserID(YHBG+UUID)
					insertUser.setUserphone(unionData.getUserphone()); // 用户手机号码
					insertUser.setUsersms(random); // 用户短信验证码
					insertUser.setUsersmstime(new Date()); //用户短信验证码创建时间
					insertUser.setUsercreatetime(new Date()); // 用户创建时间
					
					userService.insertByUser(insertUser); // 添加
				} else {
					// 根据手机号更新
					User updateUser = new User();
					updateUser.setUserphone(unionData.getUserphone()); // 用户手机号码
					updateUser.setUsersms(random); // 用户短信验证码
					updateUser.setUsersmstime(new Date()); //用户短信验证码创建时间
					
					userService.updateByUserPhone(updateUser); // 更新
				}
				
				returnData.setCount(1);
				returnData.setMsg("短信发送成功，请注意查收！");
			} else {
				returnData.setMsg("短信发送失败，" + response.getCode());
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("发送短信失败！");
			return returnData;
		}
		
		return returnData;
	}
	
	/**
	 * 注册
	 * 
	 * @param unionData
	 * @return
	 * 
	 * @author WJF on 2018/09/10
	 */
	@ResponseBody
	@RequestMapping("/register")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> register(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 注册
		int count = 0;
		
		// 查询是否存在这个手机号码
		UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
		selectUser.setUserphone(unionData.getUserphone());
		selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
		List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesByUnionData(selectUser); // 查询
		
		if (userList != null && userList.size() != 0 && userList.get(0).getUserpassword() != null && !userList.get(0).getUserpassword().isEmpty()) {
			returnData.setMsg("该手机号码已注册，请更换其他手机号码！");
			return returnData;
		}
		
		// 通过手机号码查询验证码、验证码创建时间
		UserAndUserServiceAndUserCustomer userAndUserServiceAndUserCustomer = userList.get(0);
		
		// 验证码时间差计算
        Long sendTime = userAndUserServiceAndUserCustomer.getUsersmstime().getTime(); // 验证码创建时间
        Long nowTime = new Date().getTime(); // 当前时间
        // 时间差
        Long time = nowTime - sendTime;
        
        // 验证
        if (!unionData.getUsersms().equals(userAndUserServiceAndUserCustomer.getUsersms())) {
			returnData.setMsg("请输入正确的验证码!");
			return returnData;
		} else if (time > 5 * 60 * 1000) { 
			// 5分钟
			returnData.setMsg("验证码超时，请重新获取！");
			return returnData;
		} else {
			// 更新
			User user = new User();
			user.setUserid(userAndUserServiceAndUserCustomer.getUserid());
			user.setUserrealname(unionData.getUserrealname());
			user.setUserpassword(unionData.getUserpassword());
			user.setUserlatitude(unionData.getUserlatitude());
			user.setUserlongitude(unionData.getUserlongitude());
			user.setUserinvitationuserid(unionData.getUserinvitationuserid());//邀请人UserId
			
			count = userService.updateByUser(user);
		}
            
        // 返回数据
 		if (count == 0) {
 			returnData.setMsg("注册失败！");
 		} else {
 			returnData.setCount(count);
 			returnData.setData(userList);
 			returnData.setMsg("注册成功！");
 		}
		
		return returnData;
	}

	/**
	 * 登录
	 * 
	 * 根据手机号判断登录
   	 * 
	 * @param user
	 * @return
	 * 
	 * @author WJF on 2018/09/06
	 */
	@ResponseBody
	@RequestMapping("/login")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> login(@RequestBody User user) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (user.getVersion() != null && !user.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 查询用户数据，并判断用户名、密码是否正确
		if (user.getUserphone() != null && !user.getUserphone().isEmpty() && 
				user.getUserpassword() != null && !user.getUserpassword().isEmpty()) {
			// 根据手机号码查询数据
			UserAndUserServiceAndUserCustomer selectData = new UserAndUserServiceAndUserCustomer();
			selectData.setUserphone(user.getUserphone()); // 用户手机号码
			selectData.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
			
			// 查询数据
			List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesByUnionData(selectData);
		
			// 判断用户名是否正确
			if (userList == null || userList.size() == 0 || null == userList.get(0).getUserpassword()) {
				// 没有该用户
				returnData.setMsg("没有该用户！");
			} else {
				// 判断密码是否正确
				if (!userList.get(0).getUserpassword().equals(user.getUserpassword())) {
					// 没有该用户
					returnData.setMsg("密码错误！");
				}
				else if (userList.get(0).getUserstate().equals("FJ")){
					returnData.setCode(600);
					returnData.setMsg("您账户处于封禁状态，请联系管理员！");
				}
				else {
					// Token验证
					TokenEntity tokenEntity = redisTokenManager.createToken(userList.get(0).getUserid());
					// 添加Token相关数据
					userList.get(0).setTokenUserId(tokenEntity.getUserid());
					userList.get(0).setToken(tokenEntity.getToken());
					// 需要在进行Token验证的接口方法上添加  @Authorization

					//添加登录日志
					Log loginLog = new Log();
					loginLog.setLogid(Config.SIGN_LOG + UUID.randomUUID().toString());//日志id
					loginLog.setLoguserid(userList.get(0).getUserid());//登陆人ID
					loginLog.setLogstate("DL");//状态：登陆
					loginLog.setLogcreatetime(new Date());//登陆记录的时间

					logService.insertByLog(loginLog);

					returnData.setCount(1);
					returnData.setMsg("登录成功！");
					returnData.setData(userList);
				}
			}
		} else {
			// 传递的客户数据错误
			returnData.setMsg("传递的登录数据错误！");
		}
		
		return returnData;
	}
	
	/**
	 * 注册时验证手机号是否存在
	 * 
	 * @param unionData
	 * @return
	 * 
	 * @author ZY on 2018/10/08
	 */
	@ResponseBody
	@RequestMapping("/verificationPhone")
	public LayuiDataTemplet<User> verificationPhone(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			
			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}
		
		// 查询是否存在这个手机号码
		UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
		selectUser.setUserphone(unionData.getUserphone());
		selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
		List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesByUnionData(selectUser); // 查询
		
		if (userList != null && userList.size() != 0 && userList.get(0).getUserpassword() != null && !userList.get(0).getUserpassword().isEmpty()) {
			returnData.setMsg("该手机号码已注册，请更换其他手机号码！");
			return returnData;
		}
				
		return returnData;
	}

    /**
     * 更新用户数据(管理员重置密码)
     *
     * @param user
     * @return
     *
     * @author ZY on 2018/12/03
     */
    @ResponseBody
    @RequestMapping("/updateByUserResetPassword")
    public LayuiDataTemplet<User> updateByUserResetPassword(@RequestBody User user) {
        LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
        returnData.setCode(0); // 默认为0
        returnData.setCount(0); // 数据的数量，默认为0
        returnData.setData(null); // 数据List，默认为null

        // 版本号不为空，则验证版本号
        try {
            if (user.getVersion() != null && !user.getVersion().isEmpty()) {
                // 前者大则返回一个正数，后者大返回一个负数，相等则返回0
                int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
                if (compare < 0) {
                    returnData.setMsg("版本较低，请更新版本！");
                    return returnData;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

            returnData.setMsg("版本比较发生异常！");
            return returnData;
        }

        // 更新判断
        if (user.getId() == null || user.getId() == 0) {
            if (user.getUserid() == null || user.getUserid().isEmpty()) {
                returnData.setMsg("缺少更新条件，更新失败！");
                return returnData;
            }
        }

        // 更新数据
        user.setUserpassword("123456");//重置密码为123456
        user.setUserupdatetime(new Date()); // 用户更新时间

        // 更新
        int count = 0;
        count = userService.updateByUser(user); // 更新

        // 返回数据
        if (count == 0) {
            returnData.setMsg("重置密码失败！");
        } else {
            returnData.setCount(count);
            returnData.setMsg("重置密码成功！重置为：123456");
        }

        return returnData;
    }

	/**
	 * 更新维修、安装的排序
	 *
	 * @param user
	 * @return
	 *
	 * @author WJF on 2018/09/21
	 */
	@ResponseBody
	@RequestMapping("/updateUserSortByUser")
	public LayuiDataTemplet<User> updateUserSortByUser(@RequestBody User user) {
		LayuiDataTemplet<User> returnData = new LayuiDataTemplet<User>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (user.getVersion() != null && !user.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(user.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 更新判断
		if (user.getId() == null || user.getId() == 0) {
			if (user.getUserid() == null || user.getUserid().isEmpty()) {
				returnData.setMsg("缺少更新条件，更新失败！");
				return returnData;
			}
		}

		// 更新数据
		// 查询是否存在这个手机号码
		if (user.getUserphone() != null && !user.getUserphone().isEmpty()) {
			UserAndUserServiceAndUserCustomer selectUser = new UserAndUserServiceAndUserCustomer();
			selectUser.setUserphone(user.getUserphone()); // 用户手机号码
			selectUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
			List<UserAndUserServiceAndUserCustomer> userList = null;
			userList = userService.selectThreeTablesByUnionData(selectUser); // 查询
			if (userList != null && userList.size() != 0) {
				returnData.setMsg("该手机号码已注册，请更换其他手机号码！");
				return returnData;
			}
		}
		user.setUserupdatetime(new Date()); // 用户更新时间

		// 更新
		int count = 0;
		count = userService.updateByUser(user); // 更新

		// 返回数据
		if (count == 0) {
			returnData.setMsg("更新失败！");
		} else {
			returnData.setCount(count);
			returnData.setMsg("更新成功！");
		}

		return returnData;
	}


	/**
	 * 根据UserAndUserServiceAndUserCustomer实体联表查询  维修安装按sort排序小程序首页中用到。
	 *
	 * 可以进行分页查询
	 *
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 *
	 * pageSize 每页的数据量
	 *
	 * @param unionData
	 * @return
	 *
	 * @author WJF on 2018/09/06
	 */
	@ResponseBody
	@RequestMapping("/selectThreeTablesByUnionDataOrderBySort")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> selectThreeTablesByUnionDataOrderBySort(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}

				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			}

			// 查询数量
			int count = 0;
			count = userService.selectThreeTablesCountByUnionDataOrderBySort(unionData); // 查询数量

			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("查询成功！");
				List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesByUnionDataOrderBySort(unionData); // 查询数据
				for (int i = 0; i < userList.size(); i++){
					if (null != userList.get(i).getAreacode() && !userList.get(i).getAreacode().equals("")) {
						String[] areaCodes = userList.get(i).getAreacode().split(",");
						String areaName = "";
						for (int j = 0; j < areaCodes.length; j++) {
							Area area = new Area();
							area.setPagenumber(-1);//不分页
							area.setAreaprovincecode(areaCodes[j]);
							List<Area> areaList = areaService.selectByArea(area);
							if (areaList.size() > 0) {
								areaName += areaList.get(0).getAreaprovince() + ",";
							}
						}
						userList.get(i).setAreaname(areaName);
					}
					if (null != userList.get(i).getGoodid() && !userList.get(i).getGoodid().equals("")) {
						String[] goodsids = userList.get(i).getGoodid().split(",");
						String goodsName = "";
						for (int j = 0; j < goodsids.length; j++) {
							Goods goods = new Goods();
							goods.setPagenumber(-1);//不分页
							goods.setGoodsid(goodsids[j]);
							List<Goods> goodsList = goodsService.selectByGoods(goods);
							if (goodsList.size() > 0){
								goodsName += goodsList.get(0).getGoodsname() + ",";
							}
						}
						userList.get(i).setGoodsname(goodsName);
					}
				}

				returnData.setData(userList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}


	/**
	 * 根据UserAndUserServiceAndUserCustomer实体联表查询 (客户-生成报表)
	 *
	 * 可以进行分页查询
	 *
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 *
	 * pageSize 每页的数据量
	 *
	 * @param unionData
	 * @return
	 *
	 * @author ZY on 2018/12/06
	 */
	@ResponseBody
	@RequestMapping("/selectThreeTablesBySelectDataKHReport")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> selectThreeTablesBySelectDataKHReport(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}

				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			}

			// 查询数量
			int count = 0;
			count = userService.selectThreeTablesCountBySelectDataKHReport(unionData); // 查询数量

			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setMsg("查询成功！");
				returnData.setCount(count);
				List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectDataKHReport(unionData); // 查询数据

				if (userList.size() > 0 ){
					for (int i = 0; i < userList.size(); i++) {
						double sendevaluate = 0;
						double getevaluate = 0;
						//发出的评价平均分
						EvaluateServiceAndOrderTableAndUser evaluateServiceAndOrderTableAndUser = new EvaluateServiceAndOrderTableAndUser();
						evaluateServiceAndOrderTableAndUser.setPagenumber(-1);//不分页
						evaluateServiceAndOrderTableAndUser.setCustomeruserid(userList.get(i).getUserid());//发出的评价人
						List<EvaluateServiceAndOrderTableAndUser> evaluateServiceAndOrderTableAndUserList = evaluateServiceService.selectThreeTablesByUnionData(evaluateServiceAndOrderTableAndUser);
						if (evaluateServiceAndOrderTableAndUserList.size() > 0){
							double avgTotalScore = 0;
							for (int k =0; k < evaluateServiceAndOrderTableAndUserList.size(); k++) {
								String[] evaluateservicesetupscore = evaluateServiceAndOrderTableAndUserList.get(k).getEvaluateservicesetupscore().split(",");
								//需要返回客户评价维修人员的信息
								double score = 0;

								for (int j = 0; j < evaluateservicesetupscore.length; j++) {
									score = score + Double.parseDouble(evaluateservicesetupscore[j]);
								}
								avgTotalScore =avgTotalScore + score / evaluateservicesetupscore.length;
							}
							sendevaluate = avgTotalScore/ evaluateServiceAndOrderTableAndUserList.size();
							userList.get(i).setSendevaluate(sendevaluate);
						} else {
							userList.get(i).setSendevaluate(sendevaluate);
						}

						//收到的评价平均分
						EvaluateCustomerAndOrderTableAndUser evaluateCustomerAndOrderTableAndUser = new EvaluateCustomerAndOrderTableAndUser();
						evaluateCustomerAndOrderTableAndUser.setPagenumber(-1);//不分页
						evaluateCustomerAndOrderTableAndUser.setCustomeruserid(userList.get(i).getUserid());//发出的评价人
						List<EvaluateCustomerAndOrderTableAndUser> evaluateCustomerAndOrderTableAndUserList = evaluateCustomerService.selectThreeTablesByUnionData(evaluateCustomerAndOrderTableAndUser);
						if (evaluateCustomerAndOrderTableAndUserList.size() > 0){
							double avgTotalScore = 0;
							for (int k =0; k < evaluateCustomerAndOrderTableAndUserList.size(); k++) {
								String[] evaluatecustomersetupscore = evaluateCustomerAndOrderTableAndUserList.get(k).getEvaluatecustomersetupscore().split(",");
								//需要返回客户评价维修人员的信息
								double score = 0;

								for (int j = 0; j < evaluatecustomersetupscore.length; j++) {
									score = score + Double.parseDouble(evaluatecustomersetupscore[j]);
								}
								avgTotalScore =avgTotalScore + score / evaluatecustomersetupscore.length;
							}
							getevaluate = avgTotalScore/ evaluateCustomerAndOrderTableAndUserList.size();
							userList.get(i).setGetevaluate(getevaluate);
						} else {
							userList.get(i).setGetevaluate(getevaluate);
						}
					}
				}
				//总数和平均数
				UserAndUserServiceAndUserCustomer usertotalnumber = new UserAndUserServiceAndUserCustomer();
				UserAndUserServiceAndUserCustomer usersvgnumber = new UserAndUserServiceAndUserCustomer();
				//登录次数总数、平均数
				double logintotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					logintotalnumber = logintotalnumber + userList.get(i).getLoginnumber();
				}
				usertotalnumber.setLoginnumber(logintotalnumber);
				double size = userList.size();
				usersvgnumber.setLoginnumber((logintotalnumber/size));
				//参与派单次数总数、平均数
				double pdtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					pdtotalnumber = pdtotalnumber + userList.get(i).getPdnumber();
				}
				usertotalnumber.setPdnumber(pdtotalnumber);
				usersvgnumber.setPdnumber((pdtotalnumber/size));

				//参与竞标次数总数、平均数
				double qdtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					qdtotalnumber = qdtotalnumber + userList.get(i).getQdnumber();
				}
				usertotalnumber.setQdnumber(qdtotalnumber);
				usersvgnumber.setQdnumber((qdtotalnumber/size));

				//清单列表数总数、平均数
				double projecttotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					projecttotalnumber = projecttotalnumber + userList.get(i).getProjectnumber();
				}
				usertotalnumber.setProjectnumber(projecttotalnumber);
				usersvgnumber.setProjectnumber((projecttotalnumber/size));

				//派单完成数量总数、平均数
				double pdfinishtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					pdfinishtotalnumber = pdfinishtotalnumber + userList.get(i).getPdfinishnumber();
				}
				usertotalnumber.setPdfinishnumber(pdfinishtotalnumber);
				usersvgnumber.setPdfinishnumber((pdfinishtotalnumber/size));

				//派单未完成数量总数、平均数
				double pdunfinishedtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					pdunfinishedtotalnumber = pdunfinishedtotalnumber + userList.get(i).getPdunfinishednumber();
				}
				usertotalnumber.setPdunfinishednumber(pdunfinishedtotalnumber);
				usersvgnumber.setPdunfinishednumber((pdunfinishedtotalnumber/size));

				//竞标完成数量总数、平均数
				double qdfinishtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					qdfinishtotalnumber = qdfinishtotalnumber + userList.get(i).getQdfinishnumber();
				}
				usertotalnumber.setQdfinishnumber(qdfinishtotalnumber);
				usersvgnumber.setQdfinishnumber((qdfinishtotalnumber/size));

				//竞标未完成数量总数、平均数
				double qdunfinishednumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					qdunfinishednumber = qdunfinishednumber + userList.get(i).getQdunfinishednumber();
				}
				usertotalnumber.setQdunfinishednumber(qdunfinishednumber);
				usersvgnumber.setQdunfinishednumber((qdunfinishednumber/size));

				//利润总数、平均数
				double profittotalmoney = 0;
				for (int i = 0; i < userList.size(); i ++){
					profittotalmoney = profittotalmoney + userList.get(i).getProfittotalmoney();
				}
				usertotalnumber.setProfittotalmoney(profittotalmoney);
				usersvgnumber.setProfittotalmoney((profittotalmoney/size));

				//订单合格数量总数、平均数
				double hgnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					hgnumber = hgnumber + userList.get(i).getHgnumber();
				}
				usertotalnumber.setHgnumber(hgnumber);
				usersvgnumber.setHgnumber((hgnumber/size));

				//订单不合格数量总数、平均数
				double bhgnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					bhgnumber = bhgnumber + userList.get(i).getBhgnumber();
				}
				usertotalnumber.setBhgnumber(bhgnumber);
				usersvgnumber.setBhgnumber((bhgnumber/size));

				//发出评价平均分
				double sendevaluate = 0;
				for (int i = 0; i < userList.size(); i ++){
					sendevaluate = sendevaluate + userList.get(i).getSendevaluate();
				}
				usersvgnumber.setSendevaluate((sendevaluate/size));

				//收到评价平均分
				double getevaluate = 0;
				for (int i = 0; i < userList.size(); i ++){
					getevaluate = getevaluate + userList.get(i).getGetevaluate();
				}
				usersvgnumber.setGetevaluate((getevaluate/size));

				//信用值平均分
				double usercredit = 0;
				for (int i = 0; i < userList.size(); i ++){
					usercredit = usercredit + userList.get(i).getUsercredit();
				}
				usersvgnumber.setUsercredit((usercredit/size));

				//推荐人数总数、平均数
				double recommendnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					recommendnumber = recommendnumber + userList.get(i).getRecommendnumber();
				}
				usertotalnumber.setRecommendnumber(recommendnumber);
				usersvgnumber.setRecommendnumber((recommendnumber/size));

				//添加合计、平均数
				usertotalnumber.setUserphone("合计");
				usersvgnumber.setUserphone("平台平均值");

				userList.add(usertotalnumber);
				userList.add(usersvgnumber);


				returnData.setData(userList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}


	/**
	 * 根据UserAndUserServiceAndUserCustomer实体联表查询 (维修-生成报表)
	 *
	 * 可以进行分页查询
	 *
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 *
	 * pageSize 每页的数据量
	 *
	 * @param unionData
	 * @return
	 *
	 * @author ZY on 2018/12/06
	 */
	@ResponseBody
	@RequestMapping("/selectThreeTablesBySelectDataWXReport")
	public LayuiDataTemplet<UserAndUserServiceAndUserCustomer> selectThreeTablesBySelectDataWXReport(@RequestBody UserAndUserServiceAndUserCustomer unionData) {
		LayuiDataTemplet<UserAndUserServiceAndUserCustomer> returnData = new LayuiDataTemplet<UserAndUserServiceAndUserCustomer>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}

				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			}

			// 查询数量
			int count = 0;
			count = userService.selectThreeTablesCountBySelectDataWXReport(unionData); // 查询数量

			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setMsg("查询成功！");
				List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectDataWXReport(unionData); // 查询数据
				if (userList.size() > 0 ){
					for (int i = 0; i < userList.size(); i++) {
						double sendevaluate = 0;
						double getevaluate = 0;

						//发出的评价平均分
						EvaluateCustomerAndOrderTableAndUser evaluateCustomerAndOrderTableAndUser = new EvaluateCustomerAndOrderTableAndUser();
						evaluateCustomerAndOrderTableAndUser.setPagenumber(-1);//不分页
						evaluateCustomerAndOrderTableAndUser.setServiceuserid(userList.get(i).getUserid());//发出的评价人
						List<EvaluateCustomerAndOrderTableAndUser> evaluateCustomerAndOrderTableAndUserList = evaluateCustomerService.selectThreeTablesByUnionData(evaluateCustomerAndOrderTableAndUser);
						if (evaluateCustomerAndOrderTableAndUserList.size() > 0){
							double avgTotalScore = 0;
							for (int k =0; k < evaluateCustomerAndOrderTableAndUserList.size(); k++) {
								String[] evaluatecustomersetupscore = evaluateCustomerAndOrderTableAndUserList.get(k).getEvaluatecustomersetupscore().split(",");
								//需要返回客户评价维修人员的信息
								double score = 0;

								for (int j = 0; j < evaluatecustomersetupscore.length; j++) {
									score = score + Double.parseDouble(evaluatecustomersetupscore[j]);
								}
								avgTotalScore =avgTotalScore + score / evaluatecustomersetupscore.length;
							}
							sendevaluate = avgTotalScore/ evaluateCustomerAndOrderTableAndUserList.size();
							userList.get(i).setGetevaluate(sendevaluate);
						} else {
							userList.get(i).setGetevaluate(sendevaluate);
						}

						//收到的评价平均分
						EvaluateServiceAndOrderTableAndUser evaluateServiceAndOrderTableAndUser = new EvaluateServiceAndOrderTableAndUser();
						evaluateServiceAndOrderTableAndUser.setPagenumber(-1);//不分页
						evaluateServiceAndOrderTableAndUser.setServiceuserid(userList.get(i).getUserid());//发出的评价人
						List<EvaluateServiceAndOrderTableAndUser> evaluateServiceAndOrderTableAndUserList = evaluateServiceService.selectThreeTablesByUnionData(evaluateServiceAndOrderTableAndUser);
						if (evaluateServiceAndOrderTableAndUserList.size() > 0){
							double avgTotalScore = 0;
							for (int k =0; k < evaluateServiceAndOrderTableAndUserList.size(); k++) {
								String[] evaluateservicesetupscore = evaluateServiceAndOrderTableAndUserList.get(k).getEvaluateservicesetupscore().split(",");
								//需要返回客户评价维修人员的信息
								double score = 0;

								for (int j = 0; j < evaluateservicesetupscore.length; j++) {
									score = score + Double.parseDouble(evaluateservicesetupscore[j]);
								}
								avgTotalScore =avgTotalScore + score / evaluateservicesetupscore.length;
							}
							getevaluate = avgTotalScore/ evaluateServiceAndOrderTableAndUserList.size();
							userList.get(i).setSendevaluate(getevaluate);
						} else {
							userList.get(i).setSendevaluate(getevaluate);
						}
					}
				}

				//总数和平均数
				UserAndUserServiceAndUserCustomer usertotalnumber = new UserAndUserServiceAndUserCustomer();
				UserAndUserServiceAndUserCustomer usersvgnumber = new UserAndUserServiceAndUserCustomer();
				//登录次数总数、平均数
				double logintotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					logintotalnumber = logintotalnumber + userList.get(i).getLoginnumber();
				}
				usertotalnumber.setLoginnumber(logintotalnumber);
				double size = userList.size();
				usersvgnumber.setLoginnumber((logintotalnumber/size));
				//参与派单次数总数、平均数
				double pdtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					pdtotalnumber = pdtotalnumber + userList.get(i).getPdnumber();
				}
				usertotalnumber.setPdnumber(pdtotalnumber);
				usersvgnumber.setPdnumber((pdtotalnumber/size));

				//参与竞标次数总数、平均数
				double qdtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					qdtotalnumber = qdtotalnumber + userList.get(i).getQdnumber();
				}
				usertotalnumber.setQdnumber(qdtotalnumber);
				usersvgnumber.setQdnumber((qdtotalnumber/size));

				//清单列表数总数、平均数
				double projecttotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					projecttotalnumber = projecttotalnumber + userList.get(i).getProjectnumber();
				}
				usertotalnumber.setProjectnumber(projecttotalnumber);
				usersvgnumber.setProjectnumber((projecttotalnumber/size));

				//派单完成数量总数、平均数
				double pdfinishtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					pdfinishtotalnumber = pdfinishtotalnumber + userList.get(i).getPdfinishnumber();
				}
				usertotalnumber.setPdfinishnumber(pdfinishtotalnumber);
				usersvgnumber.setPdfinishnumber((pdfinishtotalnumber/size));

				//派单未完成数量总数、平均数
				double pdunfinishedtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					pdunfinishedtotalnumber = pdunfinishedtotalnumber + userList.get(i).getPdunfinishednumber();
				}
				usertotalnumber.setPdunfinishednumber(pdunfinishedtotalnumber);
				usersvgnumber.setPdunfinishednumber((pdunfinishedtotalnumber/size));

				//竞标完成数量总数、平均数
				double qdfinishtotalnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					qdfinishtotalnumber = qdfinishtotalnumber + userList.get(i).getQdunfinishednumber();
				}
				usertotalnumber.setQdunfinishednumber(qdfinishtotalnumber);
				usersvgnumber.setQdunfinishednumber((qdfinishtotalnumber/size));

				//竞标未完成数量总数、平均数
				double qdunfinishednumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					qdunfinishednumber = qdunfinishednumber + userList.get(i).getQdfinishnumber();
				}
				usertotalnumber.setQdfinishnumber(qdunfinishednumber);
				usersvgnumber.setQdfinishnumber((qdunfinishednumber/size));


				//订单合格数量总数、平均数
				double hgnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					hgnumber = hgnumber + userList.get(i).getHgnumber();
				}
				usertotalnumber.setHgnumber(hgnumber);
				usersvgnumber.setHgnumber((hgnumber/size));

				//订单不合格数量总数、平均数
				double bhgnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					bhgnumber = bhgnumber + userList.get(i).getBhgnumber();
				}
				usertotalnumber.setBhgnumber(bhgnumber);
				usersvgnumber.setBhgnumber((bhgnumber/size));

				//发出评价平均分
				double sendevaluate = 0;
				for (int i = 0; i < userList.size(); i ++){
					sendevaluate = sendevaluate + userList.get(i).getSendevaluate();
				}
				usersvgnumber.setSendevaluate((sendevaluate/size));

				//收到评价平均分
				double getevaluate = 0;
				for (int i = 0; i < userList.size(); i ++){
					getevaluate = getevaluate + userList.get(i).getGetevaluate();
				}
				usersvgnumber.setGetevaluate((getevaluate/size));

				//信用值平均分
				double usercredit = 0;
				for (int i = 0; i < userList.size(); i ++){
					usercredit = usercredit + userList.get(i).getUsercredit();
				}
				usersvgnumber.setUsercredit((usercredit/size));

				//推荐人数总数、平均数
				double recommendnumber = 0;
				for (int i = 0; i < userList.size(); i ++){
					recommendnumber = recommendnumber + userList.get(i).getRecommendnumber();
				}
				usertotalnumber.setRecommendnumber(recommendnumber);
				usersvgnumber.setRecommendnumber((recommendnumber/size));

				//添加合计、平均数
				usertotalnumber.setUserphone("合计");
				usersvgnumber.setUserphone("平台平均值");

				userList.add(usertotalnumber);
				userList.add(usersvgnumber);

				returnData.setCount(count);

				returnData.setData(userList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}

	/**
	 * 查询日志
	 *
	 * 根据ULogAndUser实体联表模糊查询
	 *
	 * 可以进行分页查询
	 *
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 *
	 * pageSize 每页的数据量
	 *
	 * @param unionData
	 * @return
	 *
	 * @author zy on 2018/11/23
	 */
	@ResponseBody
	@RequestMapping("/selectLogAndUser")
	public LayuiDataTemplet<LogAndUser> selectLogAndUser(@RequestBody LogAndUser unionData) throws Exception {
		LayuiDataTemplet<LogAndUser> returnData = new LayuiDataTemplet<LogAndUser>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}

				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			}

			//unionData.setPagenumber(-1);
			// 查询数量
			int count = 0;
			count = userService.selectLogAndUserCount(unionData); // 查询数量

			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("查询成功！");
				List<LogAndUser> userList = userService.selectLogAndUser(unionData); // 查询数据
				returnData.setData(userList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}

	/**
	 * 根据UserAndUserServiceAndUserCustomer实体联表模糊查询
	 *
	 * 可以进行分页查询
	 *
	 * pageNumber 当前页数(如果不进行分页，该条数据默认为-1)
	 *
	 * pageSize 每页的数据量
	 *
	 * @param unionData
	 * @return
	 *
	 * @author zy on 2018/11/23
	 */
	/*@ResponseBody
	@RequestMapping("/selectThreeTablesBySelectDataToExcel")
	public LayuiDataTemplet<UserExcel> selectThreeTablesBySelectDataToExcel(@RequestBody UserAndUserServiceAndUserCustomer unionData, HttpServletRequest request, HttpServletResponse response) throws Exception {
		LayuiDataTemplet<UserExcel> returnData = new LayuiDataTemplet<UserExcel>(); // 返回数据
		returnData.setCode(0); // 默认为0
		returnData.setCount(0); // 数据的数量，默认为0
		returnData.setData(null); // 数据List，默认为null

		// 版本号不为空，则验证版本号
		try {
			if (unionData.getVersion() != null && !unionData.getVersion().isEmpty()) {
				// 前者大则返回一个正数，后者大返回一个负数，相等则返回0
				int compare = VersionCompare.compare(unionData.getVersion(), Config.VERSION);
				if (compare < 0) {
					returnData.setMsg("版本较低，请更新版本！");
					return returnData;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			returnData.setMsg("版本比较发生异常！");
			return returnData;
		}

		// 分页数据
		// 使用limit分页查询，根据偏移量查询
		// 第一个参数为第一个返回记录行的偏移量，第二个参数为返回记录行的最大数目
		// MySQL > SELECT * FROM table LIMIT 5, 10; // 查询第6-15行数据
		if (unionData.getPagenumber() != null) {
			// 计算偏移量
			if (unionData.getPagenumber() != -1) {
				if (unionData.getPagesize() == null) {
					returnData.setMsg("传递的分页数据(每页数量)错误！");
					return returnData;
				}

				// 获取传递过来的数据
				int pageNumber = unionData.getPagenumber();
				int pageSize = unionData.getPagesize();
				unionData.setPagenumber((pageNumber - 1) * pageSize); // 当前页数(如果不进行分页，该条数据默认为-1)
				unionData.setPagesize(pageSize); // 每页的数据量
			}

			unionData.setPagenumber(-1);
			// 查询数量
			int count = 0;
			count = userService.selectThreeTablesCountBySelectData(unionData); // 查询数量

			// 返回数据
			if (count == 0) {
				returnData.setMsg("暂无数据！");
			} else {
				returnData.setCount(count);
				returnData.setMsg("查询成功！");
				List<UserExcel> userList = userService.selectThreeTablesBySelectDataExcel(unionData); // 查询数据
				//导出

				*//*response.reset(); //清除buffer缓存
				String salaryDate = System.currentTimeMillis() + "";
				// 指定下载的文件名，浏览器都会使用本地编码，即GBK，浏览器收到这个文件名后，用ISO-8859-1来解码，然后用GBK来显示
				// 所以我们用GBK解码，ISO-8859-1来编码，在浏览器那边会反过来执行。
				response.setHeader("Content-Disposition", "attachment;filename=" + new String(salaryDate.getBytes("GBK"),"ISO-8859-1"));
				response.setContentType("application/vnd.ms-excel;charset=UTF-8");
				response.setHeader("Pragma", "no-cache");
				response.setHeader("Cache-Control", "no-cache");
				response.setDateHeader("Expires", 0);
				XSSFWorkbook workbook=null;
				//导出Excel对象
				List<ExcelBean> excel=new ArrayList<>();
				Map<Integer,List<ExcelBean>> map=new LinkedHashMap<>();
				XSSFWorkbook xssfWorkbook=null;

				//设置标题栏
				excel.add(new ExcelBean("序号","id",0));
				excel.add(new ExcelBean("姓名","userrealname",0));
				excel.add(new ExcelBean("电话","userphone",0));
				excel.add(new ExcelBean("角色","userrole",0));
				excel.add(new ExcelBean("状态","userstate",0));
				map.put(0, excel);
				String sheetName = "用户信息";
				//调用ExcelUtil的方法
				xssfWorkbook = ExcelUtil.createExcelFile(UserAndUserServiceAndUserCustomer.class, userList, map, sheetName);
				OutputStream output;
				try {
					output = response.getOutputStream();
					BufferedOutputStream bufferedOutPut = new BufferedOutputStream(output);
					bufferedOutPut.flush();
					workbook.write(bufferedOutPut);
					bufferedOutPut.close();
				} catch (IOException e) {
					e.printStackTrace();
				}*//*



				*//*ExportExcelUtil1 ex = new ExportExcelUtil1();
				String title = "Book信息记录";
				String[] headers = { "姓名","电话","角色","状态"};
				List<String[]> dataset = new ArrayList<String[]>();
				for(int i=0;i<userList.size();i++) {
					UserAndUserServiceAndUserCustomer book = userList.get(i);
					dataset.add(new String[]{book.getUserrealname(),book.getUserphone(),book.getUserrole(), book.getUserstate()});
				}
	        *//**//*
	        OutputStream out = null;
			try {
				out = new FileOutputStream("C://output.xls");
				ex.exportExcel(title,headers, dataset, out);
			    out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			*//**//*

				OutputStream out = null;//创建一个输出流对象
				try {
					out = response.getOutputStream();//
					response.setHeader("Content-disposition","attachment; filename="+"Book.xls");//filename是下载的xls的名，建议最好用英文
					response.setContentType("application/msexcel;charset=UTF-8");//设置类型
					response.setHeader("Pragma","No-cache");//设置头
					response.setHeader("Cache-Control","no-cache");//设置头
					response.setDateHeader("Expires", 0);//设置日期头
					String rootPath = request.getSession().getServletContext().getRealPath("/");
					ex.exportExcel(rootPath,title,headers, dataset, out);
					out.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}finally{
					try{
						if(out!=null){
							out.close();
						}
					}catch(IOException e){
						e.printStackTrace();
					}
				}*//*

				//后台返回给前台时，可以把JSON对象转化成JSON字符串
				com.alibaba.fastjson.JSONArray ja= (com.alibaba.fastjson.JSONArray) com.alibaba.fastjson.JSONArray.toJSON(userList);
				//LinkedHashMap保留插入的顺序（key,value）
				Map<String,String> headMap = new LinkedHashMap<String,String>();
				headMap.put("username","姓名");
				headMap.put("userphone","手机号");
				headMap.put("userrole","角色");
				headMap.put("userstate","状态");
				String title = "用户表 ";
				//调用工具类导出方法
				ExcelUtil1.downloadExcelFile(title,headMap,ja,response);

				returnData.setData(userList);
			}
		} else {
			returnData.setMsg("传递的分页数据(页数)错误！");
		}

		return returnData;
	}*/
}

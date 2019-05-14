package sun.bz1.restful;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import net.sf.json.JSONObject;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import sun.LayuiDataTemplet;
import sun.bz1.entity.*;
import sun.bz1.service.*;
import util.Config;
import util.JsonUtil;
import util.VersionCompare;
import util.WechatFormUtil;
import wechat.entity.KingDeeEntity;
import wechat.kingdee.InvokeHelper;

/**
 * 验收操作
 * <p>
 * 维修单保存、验收单保存
 * <p>
 * Restful
 *
 * @author ZY on 2018/09/15
 */

@Controller
@RequestMapping("/operation_check")
public class F_OperationCheckRestful {

    @Autowired
    private UserService userService;

    @Autowired
    private OrderTableService orderTableService;

    @Autowired
    private OperationCheckService operationCheckService;

    @Autowired
    private ApplyCheckService applyCheckService;

    @Autowired
    private WechatFormService wechatFormService;

    @Autowired
    private WechatService wechatService;

    // 格式化时间
    private SimpleDateFormat sdfOfDate = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat sdfOfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Logger logger = Logger.getLogger(F_OperationOrderRestful.class);

    /**
     * 申请验收
     * <p>
     * 操作过程：
     * 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
     * 2、更新维修单表(状态)
     * 3、添加验收表
     * <p>
     * 传递OrderTable实体
     * 传递ApplyCheck实体
     * <p>
     * 需在OrderTable实体中添加版本信息(可以为空)
     * 需在OrderTable实体中添加当前操作人员ID
     *
     * @param models
     * @return
     * @author ZY on 2018/09/15
     */
    @ResponseBody
    @RequestMapping("/saveOrderAndCheck")
    public LayuiDataTemplet<OrderTable> saveOrderAndCheck(@RequestBody Map<String, Object> models) {
        LayuiDataTemplet<OrderTable> returnData = new LayuiDataTemplet<OrderTable>(); // 返回数据
        returnData.setCode(0); // 默认为0
        returnData.setCount(0); // 数据的数量，默认为0
        returnData.setData(null); // 数据List，默认为null

        // JSON转换为实体类
        OrderTable orderTable = null;
        ApplyCheck applyCheck = null;
        try {
            orderTable = JsonUtil.json2obj((String) models.get("orderTable"), OrderTable.class);
            applyCheck = JsonUtil.json2obj((String) models.get("applyCheck"), ApplyCheck.class);
        } catch (Exception e1) {
            e1.printStackTrace();

            returnData.setMsg("JSON转换为实体类发生异常！");
            return returnData;
        }

        // 判断传递的数据
        // OrderTable实体
        if (orderTable == null) {
            returnData.setMsg("传递的维修单数据错误！");
            return returnData;
        }

        // 判断传递的数据
        // ApplyCheck实体
        if (applyCheck == null) {
            returnData.setMsg("传递的验收单数据错误！");
            return returnData;
        }

        // 版本号不为空，则验证版本号
        try {
            if (orderTable.getVersion() != null && !orderTable.getVersion().isEmpty()) {
                // 前者大则返回一个正数，后者大返回一个负数，相等则返回0
                int compare = VersionCompare.compare(orderTable.getVersion(), Config.VERSION);
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

        // 当前操作人员ID
        String currentUserId = orderTable.getCurrentuserid();
        if (currentUserId == null || currentUserId.isEmpty()) {
            returnData.setMsg("当前操作人ID为空，无法申请验收！");
            return returnData;
        }

        // 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
        OrderTable selectOrderTable = new OrderTable();
        selectOrderTable.setOrderid(orderTable.getOrderid());
        selectOrderTable.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<OrderTable> orderList = null;
        orderList = orderTableService.selectByOrderTable(selectOrderTable);
        if (orderList == null || orderList.size() == 0) {
            returnData.setMsg("找不到该维修单，无法申请验收！");
            return returnData;
        } else {
            orderTable = orderList.get(0);
        }

        // 2、更新维修单表数据
        orderTable.setOrderstate("SQYS"); // 维修状态(BJ:编辑/FB:发布/QD:抢单/JS:接收/WXTH:维修人员退回/KHQX:客户取消/QRQX:维修人员确认取消/SQYS:申请验收/YSHG:验收合格/YSBHG:验收不合格/GBDD:关闭订单)
        orderTable.setOrderupdateuserid(currentUserId);  // 当前操作人员ID // 维修单更新人ID(YHBG+UUID)
        orderTable.setOrderupdatetime(new Date()); // 维修单更新时间
        //添加是否全部验收字段
        ApplyCheckAndOrderTableAndUser applyCheckAndOrderTableAndUser2 = new ApplyCheckAndOrderTableAndUser();
        applyCheckAndOrderTableAndUser2.setPagenumber(-1);//不分页
        applyCheckAndOrderTableAndUser2.setOrderid(orderTable.getOrderid());//订单编号
        applyCheckAndOrderTableAndUser2.setApplycheckstate("HG");
        List<ApplyCheckAndOrderTableAndUser> list2 = null;
        list2 = applyCheckService.selectThreeTablesByUnionDataByTimeDesc(applyCheckAndOrderTableAndUser2);
        if (list2.size() == 0) {
            if ((orderTable.getOrderpricetotalmoney() + orderTable.getOrdertraveltotalmoney()) - applyCheck.getApplycheckmoney() == 0) {
                orderTable.setOrdercheckallstate("ALL_CHECK");
            }
        } else {
            double money = 0;
            for (int i = 0; i < list2.size(); i++) {
                money = money + list2.get(i).getApplycheckmoney();
            }
            if ((orderTable.getOrderpricetotalmoney() + orderTable.getOrdertraveltotalmoney()) - applyCheck.getApplycheckmoney() - money == 0) {
                orderTable.setOrdercheckallstate("ALL_CHECK");
            }
        }


        OrderTable orderTable1 = new OrderTable();
        orderTable1.setOrderstate("ALL_CHECK");//订单状态
        orderTable1.setPagenumber(-1);//不分页
        orderTable1.setOrderid(orderTable.getOrderid());//订单编号
        List<OrderTable> selectOrderList = null;
        selectOrderList = orderTableService.selectByOrderTable(orderTable1);
        if (selectOrderList.size() > 0) {
            returnData.setMsg("该维修单已经全部验收，无法提交！");
            return returnData;
        }

        /**
         // 更新数据
         int num = 0;
         num = orderTableService.updateByOrderTable(orderTable);
         if (num == 0) {
         returnData.setMsg("更新维修单表失败！");
         return returnData;
         }
         */
        // 3、添加验收单数据
        applyCheck.setApplycheckid(Config.SIGN_APPLYCHECK + UUID.randomUUID().toString()); // 维修验收ID(WXYS+UUID)
        applyCheck.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+6位流水)
        applyCheck.setApplycheckstate("SQYS"); // 维修验收状态(SQYS:申请验收)(HG:合格)((派单模式)GHWX:更换维修人员)((抢单模式)WTPD:委托平台派单/PTXT:平台协调)(ZDYS:自动验收)
        applyCheck.setApplycheckapplytime(new Date()); // 维修验收申请时间
        //查询一个小时内是否已经申请验收
        ApplyCheckAndOrderTableAndUser applyCheckAndOrderTableAndUser = new ApplyCheckAndOrderTableAndUser();
        applyCheckAndOrderTableAndUser.setPagenumber(-1);//不分页
        applyCheckAndOrderTableAndUser.setOrderid(orderTable.getOrderid());//订单编号
        List<ApplyCheckAndOrderTableAndUser> list = null;
        list = applyCheckService.selectThreeTablesByUnionDataByTimeDesc(applyCheckAndOrderTableAndUser);
        if (list.size() > 0) {
            Date createTime1 = list.get(0).getApplycheckapplytime();
            Date nowTime1 = new Date();
            long cha1 = nowTime1.getTime() - createTime1.getTime();
            if (cha1 / (1000 * 60 * 60) < 1) {
                returnData.setMsg("一个小时内不能重复验收！");
                return returnData;
            }
        }

        // 维修自动验收时间(维修申请14天后，客户不验收，则平台自动验收)
        try {
            // 计算出自动验收时间(当前时间14天后)
            Calendar curr = Calendar.getInstance();
            curr.set(Calendar.DAY_OF_MONTH, curr.get(Calendar.DAY_OF_MONTH) + 7);
            Date automaticDate = curr.getTime();
            Date automaticTime = sdfOfTime.parse(sdfOfDate.format(automaticDate) + " 00:00:00"); // 格式化时间
            applyCheck.setApplycheckautomatictime(automaticTime);
        } catch (ParseException e) {
            e.printStackTrace();
        }


        //添加维修验收平台_维修人员表
        ApplyCheckPlatform applyCheckPlatform = new ApplyCheckPlatform();
        applyCheckPlatform.setApplycheckplatformid(applyCheck.getApplycheckid());//验收ID
        applyCheckPlatform.setOrderid(applyCheck.getOrderid());//订单ID
        applyCheckPlatform.setApplycheckplatformstate(applyCheck.getApplycheckstate());//验收状态
        applyCheckPlatform.setApplycheckplatformapplytime(applyCheck.getApplycheckapplytime());//维修验收申请时间
        applyCheckPlatform.setApplycheckplatformimage(applyCheck.getApplycheckimage());//验收图片
        applyCheckPlatform.setApplycheckplatformvideo(applyCheck.getApplycheckvideo());//验收视频
        applyCheckPlatform.setApplycheckplatformautomatictime(applyCheck.getApplycheckautomatictime());
        applyCheckPlatform.setApplycheckplatformmoney(applyCheck.getApplycheckmoney());//验收金额
        applyCheckPlatform.setApplycheckplatformbackmoney(applyCheck.getApplycheckbackmoney());//
        applyCheckPlatform.setApplycheckplatformbackfault(applyCheck.getApplycheckbackfault());//
        applyCheckPlatform.setApplycheckplatformtitle(applyCheck.getApplycheckbackfault());
        applyCheckPlatform.setApplycheckplatformcontent(applyCheck.getApplycheckcontent());
        applyCheckPlatform.setApplycheckplatformuserid(applyCheck.getApplycheckuserid());
        applyCheckPlatform.setApplycheckplatformtime(applyCheck.getApplychecktime());
        applyCheckPlatform.setApplycheckplatformbackuserid(applyCheck.getApplycheckbackuserid());
        applyCheckPlatform.setApplycheckplatformbacktime(applyCheck.getApplycheckbacktime());
        applyCheckPlatform.setApplycheckplatformremark(applyCheck.getApplycheckremark());//备注第几次验收

        // 申请验收
        int count = 0;
        count = operationCheckService.saveOrderAndCheck(orderTable, applyCheck, applyCheckPlatform);

        // 返回数据
        if (count == 0) {
            returnData.setMsg("申请验收失败！");
        } else {
            // 查询数据
            UserAndUserServiceAndUserCustomer selectData = new UserAndUserServiceAndUserCustomer();
            selectData.setUserid(orderTable.getOrdercreateuserid()); // 查询
            selectData.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)

            // 查询数据
            List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectData(selectData);
            if (userList.size() != 0) {
                for (int i = 0; i < userList.size(); i++) {
                    WechatForm wechatForm = new WechatForm();
                    wechatForm.setPagenumber(-1);//不分页
                    wechatForm.setUserid(userList.get(i).getUserid());
                    List<WechatForm> wechatFormList = wechatFormService.selectByWechatForm(wechatForm); // 查询数据
                    if (wechatFormList.size() != 0) {
                        String openId = "";
                        String templateId = "";
                        String formId = "";
                        for (int j = 0; j < wechatFormList.size(); j++) {
                            Date createTime = wechatFormList.get(j).getCreatedate();
                            Date nowTime = new Date();
                            long cha = nowTime.getTime() - createTime.getTime();
                            if (cha / (1000 * 60 * 60 * 24) > 6) {
                                List<Integer> deleteList = new ArrayList<Integer>();
                                deleteList.add(wechatFormList.get(j).getId());
                                wechatFormList.remove(j);
                                wechatFormService.deleteByIdList(deleteList);
                            }
                        }
                        openId = wechatFormList.get(0).getOpenid();
                        templateId = "yGGjxErLHa_N8y5sz5Knj3X7H170CZ8hq01qgbjsxKw";
                        formId = wechatFormList.get(0).getFormid();

                        SimpleDateFormat dateFormate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                        String token = F_WechatRestful.getAccessToken(wechatService);
                        //String token = "15_UmIZU3I9v8a7awi1Wd-fhTNzaRP2qFujMRMtDgCmoZgxDDxMghRXg7GH57Fr_k28RCbJI5tznJO6tvH927dCRYIoARmaDBx4ZOjCQsZrjI59QfDHZ6YiYCeusDFYTirha23vtI-SnvFptzvTOHNiAIAZSY";
                        String jsonMsg = WechatFormUtil.makeRouteMessage(openId, templateId, formId, "", "#ff6600", orderTable.getOrderprojectname(), dateFormate.format(applyCheck.getApplycheckapplytime()), "", "");
                        boolean result = WechatFormUtil.sendTemplateMessage(token, jsonMsg);
                        List<Integer> idList = new ArrayList<Integer>();
                        idList.add(wechatFormList.get(0).getId());
                        wechatFormService.deleteByIdList(idList);

                        logger.error("服务通知result" + result);
                        System.out.println("服务通知result" + result);

                    }
                }
            }

            returnData.setCount(count);
            returnData.setMsg("申请验收成功！");
        }

        return returnData;
    }

    /**
     * 验收合格
     * <p>
     * 操作过程：
     * 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
     * 2、更新维修单表(状态)
     * 3、更新验收表(状态)
     * 4、更新任务表(状态)
     * <p>
     * 金额数据：从平台中按 维修总价+维修差旅费总价 支出金额给维修人员
     * <p>
     * 5、更新用户表(维修人员信用值+5、余额)
     * 6、添加信用值变化表
     * 7、用户_维修工、安装队_收入_金额表
     * 8、添加平台_收益金额表
     * <p>
     * 传递OrderTable实体
     * <p>
     * <p>
     * 需在OrderTable实体中添加版本信息(可以为空)
     * 需在OrderTable实体中添加当前操作人员ID
     *
     * @param orderTable
     * @return
     * @author ZY on 2018/09/15
     */
    @ResponseBody
    @RequestMapping("/updateApplyCheckQualified")
    public LayuiDataTemplet<OrderTable> updateApplyCheckQualified(@RequestBody OrderTable orderTable) throws Exception {
        LayuiDataTemplet<OrderTable> returnData = new LayuiDataTemplet<OrderTable>(); // 返回数据
        returnData.setCode(0); // 默认为0
        returnData.setCount(0); // 数据的数量，默认为0
        returnData.setData(null); // 数据List，默认为null

        // 版本号不为空，则验证版本号
        try {
            if (orderTable.getVersion() != null && !orderTable.getVersion().isEmpty()) {
                // 前者大则返回一个正数，后者大返回一个负数，相等则返回0
                int compare = VersionCompare.compare(orderTable.getVersion(), Config.VERSION);
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

        // 当前操作人员ID
        String currentUserId = orderTable.getCurrentuserid();
        if (currentUserId == null || currentUserId.isEmpty()) {
            returnData.setMsg("当前操作人ID为空，无法派单！");
            return returnData;
        }

        // 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
        OrderTable selectOrderTable = new OrderTable();
        selectOrderTable.setOrderid(orderTable.getOrderid());
        selectOrderTable.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<OrderTable> orderList = null;
        orderList = orderTableService.selectByOrderTable(selectOrderTable);
        if (orderList == null || orderList.size() == 0) {
            returnData.setMsg("找不到该维修单，无法验收合格！");
            return returnData;
        } else {
            orderTable = orderList.get(0);
        }

        // 金额的默认数据
        if (orderTable.getOrderintercepttotalmoney() == null) {
            orderTable.setOrderintercepttotalmoney(0.0);
        }
        if (orderTable.getOrderintercepttraveltotalmoney() == null) {
            orderTable.setOrderintercepttraveltotalmoney(0.0);
        }
        if (orderTable.getOrderpricetotalmoney() == null) {
            orderTable.setOrderpricetotalmoney(0.0);
        }
        if (orderTable.getOrdertraveltotalmoney() == null) {
            orderTable.setOrdertraveltotalmoney(0.0);
        }

        //查询该订单是否已经验收过
        ApplyCheckAndOrderTableAndUser applyCheckAndOrderTableAndUser = new ApplyCheckAndOrderTableAndUser();
        applyCheckAndOrderTableAndUser.setPagenumber(-1);//不分页
        applyCheckAndOrderTableAndUser.setOrderid(orderTable.getOrderid());//订单ID
        List<ApplyCheckAndOrderTableAndUser> list = null;
        list = applyCheckService.selectThreeTablesByUnionDataByTimeDesc(applyCheckAndOrderTableAndUser);
        if (!list.get(0).getApplycheckstate().equals("SQYS")) {
            returnData.setMsg("该维修单已经验收，请勿重复提交验收！");
            return returnData;
        }

        // 2、更新维修单表(状态)
        // 数据
        orderTable.setOrderstate("YSHG"); // 维修状态(BJ:编辑/FB:发布/QD:抢单/JS:接收/WXTH:维修人员退回/KHQX:客户取消/QRQX:维修人员确认取消/SQYS:申请验收/YSHG:验收合格/YSBHG:验收不合格/GBDD:关闭订单)
        orderTable.setOrderupdateuserid(currentUserId); // 当前操作人员ID // 维修更新人ID(YHBG+UUID)
        orderTable.setOrderupdatetime(new Date()); // 维修更新时间
        // 3、更新验收表(状态)
        ApplyCheck applyCheck = new ApplyCheck();
        // 数据
        applyCheck.setApplycheckstate("HG"); // 维修验收状态(SQYS:申请验收)(HG:合格)((派单模式)GHWX:更换维修人员)((抢单模式)WTPD:委托平台派单/PTXT:平台协调)(ZDYS:自动验收)
        applyCheck.setApplycheckid(list.get(0).getApplycheckid());//申请验收时的验收ID
        applyCheck.setApplycheckuserid(currentUserId); // 当前操作人员ID // 维修验收人员ID(YHBG+UUID)
        applyCheck.setApplychecktime(new Date()); // 维修验收时间
        applyCheck.setOrderid(orderTable.getOrderid());//订单编号
        //applyCheck.setApplycheckmoney(orderTable.getOrderpricetotalmoney() + orderTable.getOrdertraveltotalmoney()); // 维修验收最终结算总价(合格、平台协调状态时，才会添加这个字段的数据)
        //更新验收平台_维修人员表
        ApplyCheckPlatform applyCheckPlatform = new ApplyCheckPlatform();
        applyCheckPlatform.setApplycheckplatformstate(applyCheck.getApplycheckstate());//验收状态
        applyCheckPlatform.setApplycheckplatformid(applyCheck.getApplycheckid());//申请验收时的验收ID
        applyCheckPlatform.setApplycheckplatformuserid(applyCheck.getApplycheckuserid());// 当前操作人员ID // 维修验收人员ID(YHBG+UUID)
        applyCheckPlatform.setApplycheckplatformtime(applyCheck.getApplychecktime());//验收时间
        applyCheckPlatform.setOrderid(applyCheck.getOrderid());//订单编号
        // 4、更新任务表(状态)
        Task task = new Task();
        // 数据
        task.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
        task.setTaskstate("N"); // 维修任务状态(Y:进行中/N:已完成)
        // 金额数据：从平台中按 维修总价+维修差旅费总价 支出金额给维修人员
        //验收合格增加信用分：按维修金额计算，每2000元=1信用分，信用分保留两位小数，最后一位小数四舍五入；
        //验收不合格减少信用分：按维修金额计算，每1000元=1信用分，信用分保留两位小数，最后一位小数四舍五入；
        // 5、更新用户表(维修人员信用值+)
        double credit = 0;
        credit = orderTable.getOrderpricetotalmoney() / 2000;
        // 四舍五入，保存两位小数
        BigDecimal bg = new BigDecimal(credit).setScale(2, RoundingMode.UP);
        credit = bg.doubleValue();
        User serviceUser = new User();
        UserAndUserServiceAndUserCustomer selectServiceUser = new UserAndUserServiceAndUserCustomer();
        selectServiceUser.setUserid(orderTable.getOrderserviceuserid()); // 维修人员ID(YHBG+UUID)
        selectServiceUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<UserAndUserServiceAndUserCustomer> serviceUserList = null;
        serviceUserList = userService.selectThreeTablesByUnionData(selectServiceUser);
        if (serviceUserList == null || serviceUserList.size() == 0) {
            returnData.setMsg("没有找到该维修人员，无法验收合格！");
            return returnData;
        }
        // 数据
        serviceUser.setUserid(serviceUserList.get(0).getUserid()); // 用户ID(YHBG+UUID)
        serviceUser.setUsercredit(serviceUserList.get(0).getUsercredit() + credit); // 用户信用值
        //serviceUser.setUsermoney(serviceUserList.get(0).getUsermoney() + orderTable.getOrderpricetotalmoney() + orderTable.getOrdertraveltotalmoney()); // 用户余额
        serviceUser.setUserupdateuserid(currentUserId); // 当前操作人员ID // 用户更新人ID(YHBG+UUID)
        serviceUser.setUserupdatetime(new Date()); // 用户更新时间
        // 6、添加信用值变化表
        CreditChange creditChange = new CreditChange();
        // 数据
        creditChange.setCreditchangeid(Config.SIGN_CREDITCHANGE + UUID.randomUUID().toString()); // 信用值变化ID(XYBH+UUID)
        creditChange.setUserid(serviceUser.getUserid()); // 用户ID(YHBG+UUID)
        creditChange.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
        creditChange.setCreditchangetype("KHYSHG"); // 信用值变化类型信用值变化类型(CSZ:初始值/WXTHDD:维修人员退回订单/KHQXQR:客户取消订单/KHYSHG:客户验收合格/KHGHWX:客户更换维修人员/KHWTPD:客户委托平台派单/PLWX:客户评论维修人员/PLKH:维修人员评论客户)
        creditChange.setCreditchange(credit); // 信用值
        creditChange.setCreditchangecreatetime(new Date()); // 创建时间
        // 验收合格
        int count = 0;
		/*count = operationCheckService.updateApplyCheckQualified(orderTable, applyCheck, task,
					serviceUser, creditChange, serviceIncomeMoney, backMoney);*/
        count = operationCheckService.updateApplyCheckQualified(orderTable, applyCheck, applyCheckPlatform, task, serviceUser, creditChange);

        // 返回数据
        if (count == 0) {
            returnData.setMsg("验收合格失败！");
        } else {

            /**
             * by  lovelyz
             * 查询用户的个人信息
             * serviceUserList  维修工信息
             * customerUserList 客户信息
             */
            UserAndUserServiceAndUserCustomer selectCustomerUser = new UserAndUserServiceAndUserCustomer();
            selectCustomerUser.setUserid(orderTable.getOrdercreateuserid()); // 客户ID(YHBG+UUID)
            selectCustomerUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
            List<UserAndUserServiceAndUserCustomer> customerUserList = null;
            customerUserList = userService.selectThreeTablesByUnionData(selectCustomerUser); // 查询客户详情信息
            String UserDeptnoCode = customerUserList.get(0).getUserdeptnocode(); //客户部门编码
            String WXkingdeeusercode = serviceUserList.get(0).getKingdeeusercode(); //维修工金蝶编码
            String Userkingdeeusercode = customerUserList.get(0).getKingdeeusercode(); //客户金蝶编码
            String UserDeptnoName = customerUserList.get(0).getUserdeptnoname(); //客户部门名称
            String UserOrganizationCode = customerUserList.get(0).getUserorganizationcode(); //客户组织编码
            String UserOrganizationName = customerUserList.get(0).getUserorganizationname(); //客户组织名称
            String FCOSTID = (orderTable.getOrderprojecttype() == "WX") ? "FYXM34_SYS" : "FYXM17_ZDY"; //费用项目安装FYXM17_ZDY，维修保养费FYXM34_SYS
            //给金蝶添加验收单数据
//			InvokeHelper.POST_K3CloudURL = "http://k3.chinajiuan.com:8090/K3Cloud/";
            String dbId = InvokeHelper.dbId;
            String uid = InvokeHelper.uid;
            String pwd = InvokeHelper.pwd;
            int lang = InvokeHelper.lang;
            if (InvokeHelper.Login(dbId, uid, pwd, lang)) {
                // formID
                String sFormId = "CN_PAYAPPLY";
                // 参数
                String sContent = "{\n" +
                        "\t\"Creator\": \"\",\n" +
                        "\t\"NeedUpDateFields\": [],\n" +
                        "\t\"NeedReturnFields\": [],\n" +
                        "\t\"IsDeleteEntry\": \"True\",\n" +
                        "\t\"SubSystemId\": \"\",\n" +
                        "\t\"IsVerifyBaseDataField\": \"false\",\n" +
                        "\t\"IsEntryBatchFill\": \"True\",\n" +
                        "\t\"Model\": {\n" +
                        "\t\t\"FID\": \"0\",\n" +
                        "\t\t\"FBILLTYPEID\": {\n" +
                        "\t\t\t\"FNumber\": \"FKSQ001_SYS\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FBillNo\": \"\",\n" +
                        "\t\t\"FDATE\": \"" + sdfOfDate.format(new Date()) + "\",\n" +
                        "\t\t\"FAPPLYORGID\": {\n" +
                        "\t\t\t\"FNumber\": \"" + UserOrganizationCode + "\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FCONTACTUNITTYPE\": \"BD_Supplier\",\n" +
                        "\t\t\"FCONTACTUNIT\": {\n" +
                        "\t\t\t\"FNumber\": \"" + WXkingdeeusercode + "\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FSETTLEORGID\": {\n" +
                        "\t\t\t\"FNumber\": \"" + UserOrganizationCode + "\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FPAYORGID\": {\n" +
                        "\t\t\t\"FNumber\": \"100.00\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FPURCHASEORGID\": {\n" +
                        "\t\t\t\"FNumber\": \"100.00\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FRECTUNITTYPE\": \"BD_Supplier\",\n" +
                        "\t\t\"FRECTUNIT\": {\n" +
                        "\t\t\t\"FNumber\": \"" + WXkingdeeusercode + "\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FCURRENCYID\": {\n" +
                        "\t\t\t\"FNumber\": \"PRE001\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FAPPLYAMOUNT\": {\n" +
                        "\t\t\t\"FNumber\": \"PRE001\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FMAINBOOKID\": {\n" +
                        "\t\t\t\"FNumber\": \"PRE001\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FAPPLYAMOUNTFOR_H\": \" " + list.get(0).getApplycheckmoney() + " \",\n" +
                        "\t\t\"FPURCHASEDEPTID\": {\n" +
                        "\t\t\t\"FNumber\": \"" + UserDeptnoCode + "\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FPURCHASERGROUPID\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FPURCHASERID\": {\n" +
                        "\t\t\t\"FNumber\": \"" + Userkingdeeusercode + "\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FSALEORGID\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FSALEGROUPID\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FSALEDEPTID\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FSALEERID\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FDEPARTMENT\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FDOCUMENTSTATUS\": \"A\",\n" +
                        "\t\t\"FScanPoint\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FCANCELSTATUS\": \"A\",\n" +
                        "\t\t\"FSOURCESYSTEM\": \"\",\n" +
                        "\t\t\"FIsCredit\": \"false\",\n" +
                        "\t\t\"FBankActID\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"FSETTLECUR\": {\n" +
                        "\t\t\t\"FNumber\": \"PRE001\"\n" +
                        "\t\t},\n" +
                        "\t\t\"F_PAEZ_Base\": {\n" +
                        "\t\t\t\"FNumber\": \"" + orderTable.getKingdeeorderfnumber() + "\"\n" +
                        "\t\t},\n" +
                        "\t\t\"F_PAEZ_CheckBox\": \"false\",\n" +
                        "\t\t\"F_PAEZ_Base2\": {\n" +
                        "\t\t\t\"FNumber\": \"" + UserDeptnoCode + "\"\n" +
                        "\t\t},\n" +
                        "\t\t\"F_PAEZ_CheckBox1\": \"false\",\n" +
                        "\t\t\"F_PAEZ_Base3\": {\n" +
                        "\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t},\n" +
                        "\t\t\"F_PAEZ_Remarks\": \"\",\n" +
                        "\t\t\"FPAYAPPLYENTRY\": [{\n" +
                        "\t\t\t\"FEntryID\": \"0\",\n" +
                        "\t\t\t\"FCOSTID\": {\n" +
                        "\t\t\t\t\"FNumber\": \"" + FCOSTID + "\"\n" +
                        "\t\t\t},\n" +
                        "\t\t\t\"F_PAEZ_Base4\": {\n" +
                        "\t\t\t\t\"FNumber\": \" " + UserDeptnoCode + "\"\n" +
                        "\t\t\t},\n" +
                        "\t\t\t\"F_PAEZ_Combo\": \"02\",\n" +       //这里应该是动态获取
                        "\t\t\t\"FSETTLETYPEID\": {\n" +
                        "\t\t\t\t\"FNumber\": \"JSFS04_SYS\"\n" +
                        "\t\t\t},\n" +
                        "\t\t\t\"FPAYPURPOSEID\": {\n" +
                        "\t\t\t\t\"FNumber\": \"SFKYT08_SYS\"\n" +
                        "\t\t\t},\n" +
                        "\t\t\t\"F_PAEZ_Base1\": {\n" +
                        "\t\t\t\t\"FNumber\": \"" + orderTable.getKingdeeorderfnumber() + "\"\n" +
                        "\t\t\t},\n" +
                        "\t\t\t\"FARPURPOSEID\": {\n" +
                        "\t\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t\t},\n" +
                        "\t\t\t\"FDescription\": \"小程序中验收\",\n" +
                        "\t\t\t\"FENDDATE\": \"" + sdfOfDate.format(new Date()) + "\",\n" +
                        "\t\t\t\"FEXPECTPAYDATE\": \"" + sdfOfDate.format(new Date()) + "\",\n" +
                        "\t\t\t\"FTAXAMOUNT\": \"0\",\n" +
                        "\t\t\t\"FAPPLYAMOUNTFOR\": \"" + list.get(0).getApplycheckmoney() + "\",\n" +
                        "\t\t\t\"FApplyPclAmount\": \"0\",\n" +
                        "\t\t\t\"FApplyInstAmount\": \"0\",\n" +
                        "\t\t\t\"FEACHBANKACCOUNT\": \"\",\n" +
                        "\t\t\t\"FEACHCCOUNTNAME\": \"\",\n" +
                        "\t\t\t\"FEACHBANKNAME\": \"\",\n" +
                        "\t\t\t\"FSwiftCode\": \"\",\n" +
                        "\t\t\t\"FPRICE\": \"" + list.get(0).getApplycheckmoney() + "\",\n" +
                        "\t\t\t\"FQTY\": \"1\",\n" +
                        "\t\t\t\"FPRICEUNITID\": {\n" +
                        "\t\t\t\t\"FNumber\": \"\"\n" +
                        "\t\t\t},\n" +
                        "\t\t\t\"F_PAEZ_Combo1\": \"\",\n" +
                        "\t\t\t\"F_PAEZ_Text\": \"\",\n" +
                        "\t\t\t\"F_PAEZ_Combo2\": \"1\"\n" +
                        "\t\t}]\n" +
                        "\t}\n" +
                        "}";
                //将数据写入金蝶系统
                String str = InvokeHelper.Save(sFormId, sContent);
                JSONObject jsonObject = JSONObject.fromObject(str);
//                获取付款单id  付款单编号
                Integer kingdeeorderid = (Integer) jsonObject.getJSONObject("Result").get("Id");
                String kingdeeorderfnumber = (String) jsonObject.getJSONObject("Result").get("Number");
                /**
                 * by   lovelyz
                 * 提交审核  审批
                 * 这里有问题  带解决
                 */
//                String FormId = "CN_PAYAPPLY";
//                String Content = "{\\\"CreateOrgId\\\":\\\"" + kingdeeorderid + "\\\"," +
//                        "\\\"Numbers\\\":['" + kingdeeorderfnumber + "']," +
//                        "\\\"Ids\\\":\\\"\\\"}";
//                InvokeHelper.Submit(FormId, Content);
                System.out.println("hhhhhhhh success");
            }
            // 查询数据
            UserAndUserServiceAndUserCustomer selectData = new UserAndUserServiceAndUserCustomer();
            selectData.setUserid(orderTable.getOrderserviceuserid()); // 查询
            selectData.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
            // 查询数据
            List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectData(selectData);
            if (userList.size() != 0) {
                for (int i = 0; i < userList.size(); i++) {
                    WechatForm wechatForm = new WechatForm();
                    wechatForm.setPagenumber(-1);//不分页
                    wechatForm.setUserid(userList.get(i).getUserid());
                    List<WechatForm> wechatFormList = wechatFormService.selectByWechatForm(wechatForm); // 查询数据
                    if (wechatFormList.size() != 0) {
                        String openId = "";
                        String templateId = "";
                        String formId = "";
                        for (int j = 0; j < wechatFormList.size(); j++) {
                            Date createTime = wechatFormList.get(j).getCreatedate();
                            Date nowTime = new Date();
                            long cha = nowTime.getTime() - createTime.getTime();
                            if (cha / (1000 * 60 * 60 * 24) > 6) {
                                List<Integer> deleteList = new ArrayList<Integer>();
                                deleteList.add(wechatFormList.get(j).getId());
                                wechatFormList.remove(j);
                                wechatFormService.deleteByIdList(deleteList);
                            }
                        }
                        openId = wechatFormList.get(0).getOpenid();
                        templateId = "k6sflSt7bIhP2eW_UX-Fgpq8NG-_rEOyHUiBmMYunuE";
                        formId = wechatFormList.get(0).getFormid();
                        SimpleDateFormat dateFormate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String token = F_WechatRestful.getAccessToken(wechatService);
                        //String token = "15_UmIZU3I9v8a7awi1Wd-fhTNzaRP2qFujMRMtDgCmoZgxDDxMghRXg7GH57Fr_k28RCbJI5tznJO6tvH927dCRYIoARmaDBx4ZOjCQsZrjI59QfDHZ6YiYCeusDFYTirha23vtI-SnvFptzvTOHNiAIAZSY";
                        String jsonMsg = WechatFormUtil.makeRouteMessage(openId, templateId, formId, "", "#ff6600", "验收合格", "验收合格", dateFormate.format(applyCheck.getApplychecktime()), orderTable.getOrderid());
                        boolean result = WechatFormUtil.sendTemplateMessage(token, jsonMsg);
                        List<Integer> idList = new ArrayList<Integer>();
                        idList.add(wechatFormList.get(0).getId());
                        wechatFormService.deleteByIdList(idList);
                        logger.error("服务通知result" + result);
                        System.out.println("服务通知result" + result);

                    }
                }
            }

            //通知客户付款的服务通知
            // 查询数据
            UserAndUserServiceAndUserCustomer selectDataPay = new UserAndUserServiceAndUserCustomer();
            selectDataPay.setUserid(orderTable.getOrdercreateuserid()); // 查询
            selectDataPay.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)

            // 查询数据
            List<UserAndUserServiceAndUserCustomer> userPayList = userService.selectThreeTablesBySelectData(selectDataPay);
            if (userPayList.size() != 0) {
                for (int i = 0; i < userPayList.size(); i++) {
                    WechatForm wechatForm = new WechatForm();
                    wechatForm.setPagenumber(-1);//不分页
                    wechatForm.setUserid(userPayList.get(i).getUserid());
                    List<WechatForm> wechatFormList = wechatFormService.selectByWechatForm(wechatForm); // 查询数据
                    if (wechatFormList.size() != 0) {
                        String openId = "";
                        String templateId = "";
                        String formId = "";
                        for (int j = 0; j < wechatFormList.size(); j++) {
                            Date createTime = wechatFormList.get(j).getCreatedate();
                            Date nowTime = new Date();
                            long cha = nowTime.getTime() - createTime.getTime();
                            if (cha / (1000 * 60 * 60 * 24) > 6) {
                                List<Integer> deleteList = new ArrayList<Integer>();
                                deleteList.add(wechatFormList.get(j).getId());
                                wechatFormList.remove(j);
                                wechatFormService.deleteByIdList(deleteList);
                            }
                        }
                        openId = wechatFormList.get(0).getOpenid();
                        templateId = "Se92WfIFka7DQZxJnFgwrtwJNFi9QjKk_2MC6fYp_Hs";
                        formId = wechatFormList.get(0).getFormid();

                        SimpleDateFormat dateFormate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                        String token = F_WechatRestful.getAccessToken(wechatService);
                        //String token = "15_UmIZU3I9v8a7awi1Wd-fhTNzaRP2qFujMRMtDgCmoZgxDDxMghRXg7GH57Fr_k28RCbJI5tznJO6tvH927dCRYIoARmaDBx4ZOjCQsZrjI59QfDHZ6YiYCeusDFYTirha23vtI-SnvFptzvTOHNiAIAZSY";
                        String jsonMsg = WechatFormUtil.makeRouteMessage(openId, templateId, formId, "", "#ff6600", (orderTable.getOrderpricetotalmoney() + orderTable.getOrdertraveltotalmoney()) + "元", orderTable.getOrderprojectname(), orderTable.getOrderid(), "");
                        boolean result = WechatFormUtil.sendTemplateMessage(token, jsonMsg);
                        List<Integer> idList = new ArrayList<Integer>();
                        idList.add(wechatFormList.get(0).getId());
                        wechatFormService.deleteByIdList(idList);

                        logger.error("服务通知result" + result);
                        System.out.println("服务通知result" + result);

                    }
                }
            }
            returnData.setCount(count);
            returnData.setMsg("验收合格成功！");
        }

        return returnData;
    }

    /**
     * 验收不合格更换维修人员(派单模式)
     * <p>
     * 操作过程：
     * 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
     * 2、更新维修单表(状态)
     * 3、更新验收表(状态)
     * 4、更新任务表(状态)
     * 5、更新用户表(维修人员信用值-10)
     * 6、添加信用值变化表
     * 7、删除维修单对应维修单价表对应数据
     * <p>
     * 金额数据：从平台中按 维修推荐总价+维修差旅费推荐总价 支出金额给客户
     * <p>
     * 8、更新用户表(客户余额)
     * 9、添加用户_客户_收入_金额表
     * 10、添加平台_收益金额表
     * <p>
     * 传递OrderTable实体
     * <p>
     * 需在OrderTable实体中添加版本信息(可以为空)
     * 需在OrderTable实体中添加当前操作人员ID
     *
     * @param orderTable
     * @return
     * @author ZY on 2018/09/17
     */
    @ResponseBody
    @RequestMapping("/updateApplyCheckUnqualifiedAndChange")
    public LayuiDataTemplet<OrderTable> updateApplyCheckUnqualifiedAndChange(@RequestBody OrderTable orderTable) {
        LayuiDataTemplet<OrderTable> returnData = new LayuiDataTemplet<OrderTable>(); // 返回数据
        returnData.setCode(0); // 默认为0
        returnData.setCount(0); // 数据的数量，默认为0
        returnData.setData(null); // 数据List，默认为null

        // 版本号不为空，则验证版本号
        try {
            if (orderTable.getVersion() != null && !orderTable.getVersion().isEmpty()) {
                // 前者大则返回一个正数，后者大返回一个负数，相等则返回0
                int compare = VersionCompare.compare(orderTable.getVersion(), Config.VERSION);
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

        // 当前操作人员ID
        String currentUserId = orderTable.getCurrentuserid();
        if (currentUserId == null || currentUserId.isEmpty()) {
            returnData.setMsg("当前操作人ID为空，无法更换维修人员！");
            return returnData;
        }

        // 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
        OrderTable selectOrderTable = new OrderTable();
        selectOrderTable.setOrderid(orderTable.getOrderid());
        selectOrderTable.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<OrderTable> orderList = null;
        orderList = orderTableService.selectByOrderTable(selectOrderTable);
        if (orderList == null || orderList.size() == 0) {
            returnData.setMsg("找不到该维修单，无法更换维修人员！");
            return returnData;
        } else {
            orderTable = orderList.get(0);
        }

        // 金额的默认数据
        if (orderTable.getOrderintercepttotalmoney() == null) {
            orderTable.setOrderintercepttotalmoney(0.0);
        }
        if (orderTable.getOrderintercepttraveltotalmoney() == null) {
            orderTable.setOrderintercepttraveltotalmoney(0.0);
        }
        if (orderTable.getOrderpricetotalmoney() == null) {
            orderTable.setOrderpricetotalmoney(0.0);
        }
        if (orderTable.getOrdertraveltotalmoney() == null) {
            orderTable.setOrdertraveltotalmoney(0.0);
        }


        //查询该订单是否已经验收过
        ApplyCheckAndOrderTableAndUser applyCheckAndOrderTableAndUser = new ApplyCheckAndOrderTableAndUser();
        applyCheckAndOrderTableAndUser.setPagenumber(-1);//不分页
        applyCheckAndOrderTableAndUser.setOrderid(orderTable.getOrderid());//订单ID
        List<ApplyCheckAndOrderTableAndUser> list = null;
        list = applyCheckService.selectThreeTablesByUnionDataByTimeDesc(applyCheckAndOrderTableAndUser);
        if (!list.get(0).getApplycheckstate().equals("SQYS")) {
            returnData.setMsg("该维修单已经验收，请勿重复提交验收！");
            return returnData;
        }

        // 2、更新维修单表(状态)
        orderTable.setOrderstate("YSBHG"); // 维修状态(BJ:编辑/FB:发布/QD:抢单/JS:接收/WXTH:维修人员退回/KHQX:客户取消/QRQX:维修人员确认取消/SQYS:申请验收/YSHG:验收合格/YSBHG:验收不合格/GBDD:关闭订单)
        orderTable.setOrderupdateuserid(currentUserId); // 当前操作人员ID // 维修更新人ID(YHBG+UUID)
        orderTable.setOrderupdatetime(new Date()); // 维修更新时间

        // 3、更新验收表
        ApplyCheck applyCheck = new ApplyCheck();
        // 数据
        applyCheck.setOrderid(orderTable.getOrderid()); // 订单ID
        applyCheck.setApplycheckid(list.get(0).getApplycheckid());//最新申请的验收id
        applyCheck.setApplycheckstate("GHWX"); // 维修验收状态(SQYS:申请验收)(HG:合格)((派单模式)GHWX:更换维修人员)((抢单模式)WTPD:委托平台派单/PTXT:平台协调)(ZDYS:自动验收)
        applyCheck.setApplycheckuserid(currentUserId); // 维修验收人员ID(YHBG+UUID)
        applyCheck.setApplychecktime(new Date()); // 维修验收时间


        // 4、更新任务表(状态)
        Task task = new Task();
        // 数据
        task.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
        task.setTaskstate("N"); // 维修任务状态(Y:进行中/N:已完成)

        //验收合格增加信用分：按维修金额计算，每2000元=1信用分，信用分保留两位小数，最后一位小数四舍五入；
        //验收不合格减少信用分：按维修金额计算，每1000元=1信用分，信用分保留两位小数，最后一位小数四舍五入；
        // 5、更新用户表(维修人员信用值-)
        double credit = 0;
        credit = orderTable.getOrderpricetotalmoney() / 1000;
        // 四舍五入，保存两位小数
        BigDecimal bg = new BigDecimal(credit).setScale(2, RoundingMode.UP);
        credit = bg.doubleValue();

        User serviceUser = new User();
        // 查询
        UserAndUserServiceAndUserCustomer selectServiceUser = new UserAndUserServiceAndUserCustomer();
        selectServiceUser.setUserid(orderTable.getOrderserviceuserid()); // 维修人员ID(YHBG+UUID)
        selectServiceUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<UserAndUserServiceAndUserCustomer> serviceUserList = null;
        serviceUserList = userService.selectThreeTablesByUnionData(selectServiceUser);
        if (serviceUserList == null || serviceUserList.size() == 0) {
            returnData.setMsg("没有找到该维修人员，无法更换维修人员！");
            return returnData;
        }
        // 数据
        serviceUser.setUserid(serviceUserList.get(0).getUserid()); // 用户ID(YHBG+UUID)
        serviceUser.setUsercredit(serviceUserList.get(0).getUsercredit() - credit); // 用户信用值
        serviceUser.setUserupdateuserid(currentUserId); // 当前操作人员ID // 用户更新人ID(YHBG+UUID)
        serviceUser.setUserupdatetime(new Date()); // 用户更新时间

        // 6、添加信用值变化表
        CreditChange creditChange = new CreditChange();
        // 数据
        creditChange.setCreditchangeid(Config.SIGN_CREDITCHANGE + UUID.randomUUID().toString()); // 信用值变化ID(XYBH+UUID)
        creditChange.setUserid(serviceUser.getUserid()); // 用户ID(YHBG+UUID)
        creditChange.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
        creditChange.setCreditchangetype("KHGHWX"); // 信用值变化类型信用值变化类型(CSZ:初始值/WXTHDD:维修人员退回订单/KHQXQR:客户取消订单/KHYSHG:客户验收合格/KHGHWX:客户更换维修人员/KHWTPD:客户委托平台派单/PLWX:客户评论维修人员/PLKH:维修人员评论客户)
        creditChange.setCreditchange(-credit); // 信用值
        creditChange.setCreditchangecreatetime(new Date()); // 信用值创建时间

        // 7、删除维修单对应维修单价表对应数据
        OrderPrice orderPrice = new OrderPrice();
        orderPrice.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)

        // 金额数据：从平台中按 维修推荐总价+维修差旅费推荐总价 支出金额给客户
		
		/*// 8、更新用户表(客户余额)
		User customerUser = new User();
		// 查询
		UserAndUserServiceAndUserCustomer selectCustomerUser = new UserAndUserServiceAndUserCustomer();
		selectCustomerUser.setUserid(orderTable.getOrdercreateuserid()); // 客户ID(YHBG+UUID)
		selectCustomerUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
		List<UserAndUserServiceAndUserCustomer> customerUserList = null; 
		customerUserList = userService.selectThreeTablesByUnionData(selectCustomerUser);
		if (customerUserList == null || customerUserList.size() == 0) {
			returnData.setMsg("没有找到该客户，无法更换维修人员！");
			return returnData;
		}
		// 数据
		customerUser.setUserid(customerUserList.get(0).getUserid()); // 用户ID(YHBG+UUID)
		customerUser.setUsermoney(customerUserList.get(0).getUsermoney() + orderTable.getOrderintercepttotalmoney() + orderTable.getOrderintercepttraveltotalmoney()); // 用户余额
		customerUser.setUserupdateuserid(currentUserId); // 当前操作人员ID // 用户更新人ID(YHBG+UUID)
		customerUser.setUserupdatetime(new Date()); // 用户更新时间*/
		
		/*// 9、添加用户_客户_收入_金额表
		UserCustomerIncomeMoney customerIncomeMoney = new UserCustomerIncomeMoney();
		// 数据
		customerIncomeMoney.setUsercustomerincomemoneyid(Config.SIGN_USERCUSTOMERINCOMEMONEY + UUID.randomUUID().toString()); // 客户收入金额ID(KHSR+UUID)
		customerIncomeMoney.setUsercustomerincomemoneyuserid(customerUser.getUserid()); // 客户ID(YHBG+UUID)
		customerIncomeMoney.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
		customerIncomeMoney.setUsercustomerincomemoneysource("GHWX"); // 金额来源(WXTH:维修人员退回(客户收入金额)/QRQXKH:确认取消(客户收入金额)/GHWX:更换维修人员(客户收入金额)/WTPD:委托平台派单(客户收入金额)/PTXTKH:平台协调(客户收入金额)/KHCZ:客户充值(客户收入金额))
		customerIncomeMoney.setUsercustomerincomemoney(orderTable.getOrderintercepttotalmoney() + orderTable.getOrderintercepttraveltotalmoney()); // 金额(可正可负)
		customerIncomeMoney.setUsercustomerincomemoneycreatetime(new Date()); // 金额创建时间*/
		
		/*// 10、添加平台_收益金额表
		BackMoney backMoney = new BackMoney();
		// 数据
		backMoney.setBackmoneyid(Config.SIGN_BACKMONEY + UUID.randomUUID().toString()); // 金额变化ID(PTJE+UUID)
		backMoney.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
		backMoney.setBackmoneyother1("GHWX"); // 金额来源(PTPD:平台派单(按维修推荐总价+维修差旅费推荐总价收入金额)/WXTH:维修人员退回(支出金额给客户)/QRQXKH:确认取消(支出金额给客户)/QRQXWX:确认取消(支出金额给维修人员)/YSHG:验收合格(按维修总价+维修差旅费总价支出金额给维修人员)/GHWX:更换维修人员(按维修推荐总价+维修差旅费推荐总价支出金额给客户)/WTPD:委托平台派单(按维修推荐总价+维修差旅费推荐总价支出金额给客户)/PTXTWX:平台协调(支出金额给维修人员)/PTXTKH:平台协调(支出金额给客户))
		backMoney.setBackmoney(-(orderTable.getOrderintercepttotalmoney() + orderTable.getOrderintercepttraveltotalmoney())); // 金额(可正可负)
		backMoney.setBackmoneycreatetime(new Date()); // 金额创建时间*/

        // 验收不合格更换维修人员
        int count = 0;
		/*count = operationCheckService.updateApplyCheckUnqualifiedAndChange(orderTable, applyCheck,
				task, serviceUser, creditChange, orderPrice, customerUser, customerIncomeMoney, backMoney);*/
        count = operationCheckService.updateApplyCheckUnqualifiedAndChange(orderTable, applyCheck,
                task, serviceUser, creditChange, orderPrice);

        // 返回数据
        if (count == 0) {
            returnData.setMsg("验收不合格更换维修人员失败！");
        } else {
            // 查询数据
            UserAndUserServiceAndUserCustomer selectData = new UserAndUserServiceAndUserCustomer();
            selectData.setUserid(orderTable.getOrderserviceuserid()); // 查询
            selectData.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)

            // 查询数据
            List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectData(selectData);
            if (userList.size() != 0) {
                for (int i = 0; i < userList.size(); i++) {
                    WechatForm wechatForm = new WechatForm();
                    wechatForm.setPagenumber(-1);//不分页
                    wechatForm.setUserid(userList.get(i).getUserid());
                    List<WechatForm> wechatFormList = wechatFormService.selectByWechatForm(wechatForm); // 查询数据
                    if (wechatFormList.size() != 0) {
                        String openId = "";
                        String templateId = "";
                        String formId = "";
                        for (int j = 0; j < wechatFormList.size(); j++) {
                            Date createTime = wechatFormList.get(j).getCreatedate();
                            Date nowTime = new Date();
                            long cha = nowTime.getTime() - createTime.getTime();
                            if (cha / (1000 * 60 * 60 * 24) > 6) {
                                List<Integer> deleteList = new ArrayList<Integer>();
                                deleteList.add(wechatFormList.get(j).getId());
                                wechatFormList.remove(j);
                                wechatFormService.deleteByIdList(deleteList);
                            }
                        }
                        openId = wechatFormList.get(0).getOpenid();
                        templateId = "k6sflSt7bIhP2eW_UX-Fgpq8NG-_rEOyHUiBmMYunuE";
                        formId = wechatFormList.get(0).getFormid();

                        SimpleDateFormat dateFormate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                        String token = F_WechatRestful.getAccessToken(wechatService);
                        //String token = "15_UmIZU3I9v8a7awi1Wd-fhTNzaRP2qFujMRMtDgCmoZgxDDxMghRXg7GH57Fr_k28RCbJI5tznJO6tvH927dCRYIoARmaDBx4ZOjCQsZrjI59QfDHZ6YiYCeusDFYTirha23vtI-SnvFptzvTOHNiAIAZSY";
                        String jsonMsg = WechatFormUtil.makeRouteMessage(openId, templateId, formId, "", "#ff6600", "验收不合格", "验收不合格", dateFormate.format(applyCheck.getApplychecktime()), orderTable.getOrderid());
                        boolean result = WechatFormUtil.sendTemplateMessage(token, jsonMsg);
                        List<Integer> idList = new ArrayList<Integer>();
                        idList.add(wechatFormList.get(0).getId());
                        wechatFormService.deleteByIdList(idList);

                        logger.error("服务通知result" + result);
                        System.out.println("服务通知result" + result);

                    }
                }
            }
            returnData.setCount(count);
            returnData.setMsg("验收不合格更换维修人员成功！");
        }

        return returnData;
    }

    /**
     * 验收不合格委托平台派单(抢单模式)
     * <p>
     * 操作过程：
     * 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
     * 2、更新维修单表(状态)
     * 3、更新验收表(状态)
     * 4、更新任务表(状态)
     * 5、更新用户表(维修人员信用值-10)
     * 6、添加信用值变化表
     * 7、删除维修单对应维修单价表对应数据
     * <p>
     * 金额数据：从平台中按 维修推荐总价+维修差旅费推荐总价 支出金额给客户
     * <p>
     * 8、更新用户表(客户余额)
     * 9、添加用户_客户_收入_金额表
     * 10、添加平台_收益金额表
     * <p>
     * 传递OrderTable实体
     * <p>
     * 需在OrderTable实体中添加版本信息(可以为空)
     * 需在OrderTable实体中添加当前操作人员ID
     *
     * @param orderTable
     * @return
     * @author ZY on 2018/09/17
     */
    @ResponseBody
    @RequestMapping("/updateApplyCheckUnqualifiedAndPicketing")
    public LayuiDataTemplet<OrderTable> updateApplyCheckUnqualifiedAndPicketing(@RequestBody OrderTable orderTable) {
        LayuiDataTemplet<OrderTable> returnData = new LayuiDataTemplet<OrderTable>(); // 返回数据
        returnData.setCode(0); // 默认为0
        returnData.setCount(0); // 数据的数量，默认为0
        returnData.setData(null); // 数据List，默认为null

        // 版本号不为空，则验证版本号
        try {
            if (orderTable.getVersion() != null && !orderTable.getVersion().isEmpty()) {
                // 前者大则返回一个正数，后者大返回一个负数，相等则返回0
                int compare = VersionCompare.compare(orderTable.getVersion(), Config.VERSION);
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

        // 当前操作人员ID
        String currentUserId = orderTable.getCurrentuserid();
        if (currentUserId == null || currentUserId.isEmpty()) {
            returnData.setMsg("当前操作人ID为空，无法委托平台！");
            return returnData;
        }

        // 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
        OrderTable selectOrderTable = new OrderTable();
        selectOrderTable.setOrderid(orderTable.getOrderid());
        selectOrderTable.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<OrderTable> orderList = null;
        orderList = orderTableService.selectByOrderTable(selectOrderTable);
        if (orderList == null || orderList.size() == 0) {
            returnData.setMsg("找不到该维修单，无法委托平台！");
            return returnData;
        } else {
            orderTable = orderList.get(0);
        }

        //查询该订单是否已经验收过
        ApplyCheckAndOrderTableAndUser applyCheckAndOrderTableAndUser = new ApplyCheckAndOrderTableAndUser();
        applyCheckAndOrderTableAndUser.setPagenumber(-1);//不分页
        applyCheckAndOrderTableAndUser.setOrderid(orderTable.getOrderid());//订单ID
        List<ApplyCheckAndOrderTableAndUser> list = null;
        list = applyCheckService.selectThreeTablesByUnionDataByTimeDesc(applyCheckAndOrderTableAndUser);
        if (!list.get(0).getApplycheckstate().equals("SQYS")) {
            returnData.setMsg("该维修单已经验收，请勿重复提交验收！");
            return returnData;
        }

        // 2、更新维修单表(状态)
        orderTable.setOrderstate("YSBHG"); // 维修状态(BJ:编辑/FB:发布/QD:抢单/JS:接收/WXTH:维修人员退回/KHQX:客户取消/QRQX:维修人员确认取消/SQYS:申请验收/YSHG:验收合格/YSBHG:验收不合格/GBDD:关闭订单)
        orderTable.setOrderupdateuserid(currentUserId); // 当前操作人员ID // 维修更新人ID(YHBG+UUID)
        orderTable.setOrderupdatetime(new Date()); // 维修更新时间

        // 3、更新验收表
        ApplyCheck applyCheck = new ApplyCheck();
        // 数据
        applyCheck.setOrderid(orderTable.getOrderid()); // 订单ID
        applyCheck.setApplycheckid(list.get(0).getApplycheckid()); // 验收ID
        applyCheck.setApplycheckstate("WTPD"); // 维修验收状态(SQYS:申请验收)(HG:合格)((派单模式)GHWX:更换维修人员)((抢单模式)WTPD:委托平台派单/PTXT:平台协调)(ZDYS:自动验收)
        applyCheck.setApplycheckuserid(currentUserId); // 维修验收人员ID(YHBG+UUID)
        applyCheck.setApplychecktime(new Date()); // 维修验收时间


        // 4、更新任务表(状态)
        Task task = new Task();
        // 数据
        task.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
        task.setTaskstate("N"); // 维修任务状态(Y:进行中/N:已完成)

        //验收合格增加信用分：按维修金额计算，每2000元=1信用分，信用分保留两位小数，最后一位小数四舍五入；
        //验收不合格减少信用分：按维修金额计算，每1000元=1信用分，信用分保留两位小数，最后一位小数四舍五入；
        // 5、更新用户表(维修人员信用值-)
        double credit = 0;
        credit = orderTable.getOrderpricetotalmoney() / 1000;
        // 四舍五入，保存两位小数
        BigDecimal bg = new BigDecimal(credit).setScale(2, RoundingMode.UP);
        credit = bg.doubleValue();

        User serviceUser = new User();
        // 查询
        UserAndUserServiceAndUserCustomer selectServiceUser = new UserAndUserServiceAndUserCustomer();
        selectServiceUser.setUserid(orderTable.getOrderserviceuserid()); // 维修人员ID(YHBG+UUID)
        selectServiceUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<UserAndUserServiceAndUserCustomer> serviceUserList = null;
        serviceUserList = userService.selectThreeTablesByUnionData(selectServiceUser);
        if (serviceUserList == null || serviceUserList.size() == 0) {
            returnData.setMsg("没有找到该维修人员，无法委托平台！");
            return returnData;
        }
        // 数据
        serviceUser.setUserid(serviceUserList.get(0).getUserid()); // 用户ID(YHBG+UUID)
        serviceUser.setUsercredit(serviceUserList.get(0).getUsercredit() - credit); // 用户信用值
        serviceUser.setUserupdateuserid(currentUserId); // 当前操作人员ID // 用户更新人ID(YHBG+UUID)
        serviceUser.setUserupdatetime(new Date()); // 用户更新时间

        // 6、添加信用值变化表
        CreditChange creditChange = new CreditChange();
        // 数据
        creditChange.setCreditchangeid(Config.SIGN_CREDITCHANGE + UUID.randomUUID().toString()); // 信用值变化ID(XYBH+UUID)
        creditChange.setUserid(serviceUser.getUserid()); // 用户ID(YHBG+UUID)
        creditChange.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
        creditChange.setCreditchangetype("KHWTPD"); // 信用值变化类型信用值变化类型(CSZ:初始值/WXTHDD:维修人员退回订单/KHQXQR:客户取消订单/KHYSHG:客户验收合格/KHGHWX:客户更换维修人员/KHWTPD:客户委托平台派单/PLWX:客户评论维修人员/PLKH:维修人员评论客户)
        creditChange.setCreditchange(-credit); // 信用值
        creditChange.setCreditchangecreatetime(new Date()); // 信用值创建时间

        // 7、删除维修单对应维修单价表对应数据
        OrderPrice orderPrice = new OrderPrice();
        orderPrice.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)

        // 金额数据：从平台中按 维修推荐总价+维修差旅费推荐总价 支出金额给客户
				
		/*// 8、更新用户表(客户余额)
		User customerUser = new User();
		// 查询
		UserAndUserServiceAndUserCustomer selectCustomerUser = new UserAndUserServiceAndUserCustomer();
		selectCustomerUser.setUserid(orderTable.getOrdercreateuserid()); // 客户ID(YHBG+UUID)
		selectCustomerUser.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
		List<UserAndUserServiceAndUserCustomer> customerUserList = null; 
		customerUserList = userService.selectThreeTablesByUnionData(selectCustomerUser);
		if (customerUserList == null || customerUserList.size() == 0) {
			returnData.setMsg("没有找到该客户，无法委托平台！");
			return returnData;
		}
		// 数据
		customerUser.setUserid(customerUserList.get(0).getUserid()); // 用户ID(YHBG+UUID)
		customerUser.setUsermoney(customerUserList.get(0).getUsermoney() + orderTable.getOrderintercepttotalmoney() + orderTable.getOrderintercepttraveltotalmoney()); // 用户余额
		customerUser.setUserupdateuserid(currentUserId); // 当前操作人员ID // 用户更新人ID(YHBG+UUID)
		customerUser.setUserupdatetime(new Date()); // 用户更新时间*/
				
		/*// 9、添加用户_客户_收入_金额表
		UserCustomerIncomeMoney	customerIncomeMoney = new UserCustomerIncomeMoney();
		// 数据
		customerIncomeMoney.setUsercustomerincomemoneyid(Config.SIGN_USERCUSTOMERINCOMEMONEY + UUID.randomUUID().toString()); // 客户收入金额ID(KHSR+UUID)
		customerIncomeMoney.setUsercustomerincomemoneyuserid(customerUser.getUserid()); // 客户ID(YHBG+UUID)
		customerIncomeMoney.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
		customerIncomeMoney.setUsercustomerincomemoneysource("WTPD"); // 金额来源(WXTH:维修人员退回(客户收入金额)/QRQXKH:确认取消(客户收入金额)/GHWX:更换维修人员(客户收入金额)/WTPD:委托平台派单(客户收入金额)/PTXTKH:平台协调(客户收入金额)/KHCZ:客户充值(客户收入金额))
		customerIncomeMoney.setUsercustomerincomemoney(orderTable.getOrderintercepttotalmoney() + orderTable.getOrderintercepttraveltotalmoney()); // 金额(可正可负)
		customerIncomeMoney.setUsercustomerincomemoneycreatetime(new Date()); // 金额创建时间*/
				
		/*// 10、添加平台_收益金额表
		BackMoney backMoney = new BackMoney();
		// 数据
		backMoney.setBackmoneyid(Config.SIGN_BACKMONEY + UUID.randomUUID().toString()); // 金额变化ID(PTJE+UUID)
		backMoney.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
		backMoney.setBackmoneyother1("WTPD"); // 金额来源(PTPD:平台派单(按维修推荐总价+维修差旅费推荐总价收入金额)/WXTH:维修人员退回(支出金额给客户)/QRQXKH:确认取消(支出金额给客户)/QRQXWX:确认取消(支出金额给维修人员)/YSHG:验收合格(按维修总价+维修差旅费总价支出金额给维修人员)/GHWX:更换维修人员(按维修推荐总价+维修差旅费推荐总价支出金额给客户)/WTPD:委托平台派单(按维修推荐总价+维修差旅费推荐总价支出金额给客户)/PTXTWX:平台协调(支出金额给维修人员)/PTXTKH:平台协调(支出金额给客户))
		backMoney.setBackmoney(-(orderTable.getOrderintercepttotalmoney() + orderTable.getOrderintercepttraveltotalmoney())); // 金额(可正可负)
		backMoney.setBackmoneycreatetime(new Date()); // 金额创建时间			*/

        // 验收不合格委托平台
        int count = 0;
		/*count = operationCheckService.updateApplyCheckUnqualifiedAndPicketing(orderTable, applyCheck,
				task, serviceUser, creditChange, orderPrice, customerUser, customerIncomeMoney, backMoney);*/
        count = operationCheckService.updateApplyCheckUnqualifiedAndPicketing(orderTable, applyCheck,
                task, serviceUser, creditChange, orderPrice);
        // 返回数据
        if (count == 0) {
            returnData.setMsg("验收不合格委托平台失败！");
        } else {

            // 查询数据
            UserAndUserServiceAndUserCustomer selectData = new UserAndUserServiceAndUserCustomer();
            selectData.setUserid(orderTable.getOrderserviceuserid()); // 查询
            selectData.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)

            // 查询数据
            List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectData(selectData);
            if (userList.size() != 0) {
                for (int i = 0; i < userList.size(); i++) {
                    WechatForm wechatForm = new WechatForm();
                    wechatForm.setPagenumber(-1);//不分页
                    wechatForm.setUserid(userList.get(i).getUserid());
                    List<WechatForm> wechatFormList = wechatFormService.selectByWechatForm(wechatForm); // 查询数据
                    if (wechatFormList.size() != 0) {
                        String openId = "";
                        String templateId = "";
                        String formId = "";
                        for (int j = 0; j < wechatFormList.size(); j++) {
                            Date createTime = wechatFormList.get(j).getCreatedate();
                            Date nowTime = new Date();
                            long cha = nowTime.getTime() - createTime.getTime();
                            if (cha / (1000 * 60 * 60 * 24) > 6) {
                                List<Integer> deleteList = new ArrayList<Integer>();
                                deleteList.add(wechatFormList.get(j).getId());
                                wechatFormList.remove(j);
                                wechatFormService.deleteByIdList(deleteList);
                            }
                        }
                        openId = wechatFormList.get(0).getOpenid();
                        templateId = "k6sflSt7bIhP2eW_UX-Fgpq8NG-_rEOyHUiBmMYunuE";
                        formId = wechatFormList.get(0).getFormid();

                        SimpleDateFormat dateFormate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                        String token = F_WechatRestful.getAccessToken(wechatService);
                        //String token = "15_UmIZU3I9v8a7awi1Wd-fhTNzaRP2qFujMRMtDgCmoZgxDDxMghRXg7GH57Fr_k28RCbJI5tznJO6tvH927dCRYIoARmaDBx4ZOjCQsZrjI59QfDHZ6YiYCeusDFYTirha23vtI-SnvFptzvTOHNiAIAZSY";
                        String jsonMsg = WechatFormUtil.makeRouteMessage(openId, templateId, formId, "", "#ff6600", "验收不合格", "验收不合格", dateFormate.format(applyCheck.getApplychecktime()), orderTable.getOrderid());
                        boolean result = WechatFormUtil.sendTemplateMessage(token, jsonMsg);
                        List<Integer> idList = new ArrayList<Integer>();
                        idList.add(wechatFormList.get(0).getId());
                        wechatFormService.deleteByIdList(idList);

                        logger.error("服务通知result" + result);
                        System.out.println("服务通知result" + result);

                    }
                }
            }
            returnData.setCount(count);
            returnData.setMsg("验收不合格委托平台成功！");
        }

        return returnData;
    }

    /**
     * 验收不合格转平台客服(抢单模式)
     * <p>
     * 操作过程：
     * 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
     * 2、更新维修单表(状态)
     * 3、更新验收表(状态)
     * 4、更新任务表(状态)
     * <p>
     * 传递OrderTable实体
     * <p>
     * 需在OrderTable实体中添加版本信息(可以为空)
     * 需在OrderTable实体中添加当前操作人员ID
     *
     * @param orderTable
     * @return
     * @author ZY on 2018/09/17
     */
    @ResponseBody
    @RequestMapping("/updateApplyCheckUnqualifiedAndChangeService")
    public LayuiDataTemplet<OrderTable> updateApplyCheckUnqualifiedAndChangeService(@RequestBody OrderTable orderTable) {
        LayuiDataTemplet<OrderTable> returnData = new LayuiDataTemplet<OrderTable>(); // 返回数据
        returnData.setCode(0); // 默认为0
        returnData.setCount(0); // 数据的数量，默认为0
        returnData.setData(null); // 数据List，默认为null

        // 版本号不为空，则验证版本号
        try {
            if (orderTable.getVersion() != null && !orderTable.getVersion().isEmpty()) {
                // 前者大则返回一个正数，后者大返回一个负数，相等则返回0
                int compare = VersionCompare.compare(orderTable.getVersion(), Config.VERSION);
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

        // 当前操作人员ID
        String currentUserId = orderTable.getCurrentuserid();
        if (currentUserId == null || currentUserId.isEmpty()) {
            returnData.setMsg("当前操作人ID为空，无法转平台客服！");
            return returnData;
        }

        // 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
        OrderTable selectOrderTable = new OrderTable();
        selectOrderTable.setOrderid(orderTable.getOrderid());
        selectOrderTable.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<OrderTable> orderList = null;
        orderList = orderTableService.selectByOrderTable(selectOrderTable);
        if (orderList == null || orderList.size() == 0) {
            returnData.setMsg("找不到该维修单，无法转平台客服！");
            return returnData;
        } else {
            orderTable = orderList.get(0);
        }

        //查询该订单是否已经验收过
        ApplyCheckAndOrderTableAndUser applyCheckAndOrderTableAndUser = new ApplyCheckAndOrderTableAndUser();
        applyCheckAndOrderTableAndUser.setPagenumber(-1);//不分页
        applyCheckAndOrderTableAndUser.setOrderid(orderTable.getOrderid());//订单ID
        List<ApplyCheckAndOrderTableAndUser> list = null;
        list = applyCheckService.selectThreeTablesByUnionDataByTimeDesc(applyCheckAndOrderTableAndUser);
        if (!list.get(0).getApplycheckstate().equals("SQYS")) {
            returnData.setMsg("该维修单已经验收，请勿重复提交验收！");
            return returnData;
        }

        // 2、更新维修单表(状态)
        orderTable.setOrderstate("GBDD"); // 维修状态(BJ:编辑/FB:发布/QD:抢单/JS:接收/WXTH:维修人员退回/KHQX:客户取消/QRQX:维修人员确认取消/SQYS:申请验收/YSHG:验收合格/YSBHG:验收不合格/GBDD:关闭订单)
        orderTable.setOrderupdateuserid(currentUserId); // 当前操作人员ID // 维修更新人ID(YHBG+UUID)
        orderTable.setOrderupdatetime(new Date()); // 维修更新时间

        // 3、更新验收表
        ApplyCheck applyCheck = new ApplyCheck();
        // 数据
        applyCheck.setOrderid(orderTable.getOrderid()); // 订单ID
        applyCheck.setApplycheckid(list.get(0).getApplycheckid()); // 验收ID
        applyCheck.setApplycheckstate("PTXT"); // 维修验收状态(SQYS:申请验收)(HG:合格)((派单模式)GHWX:更换维修人员)((抢单模式)WTPD:委托平台派单/PTXT:平台协调)(ZDYS:自动验收)
        applyCheck.setApplycheckuserid(currentUserId); // 维修验收人员ID(YHBG+UUID)
        applyCheck.setApplychecktime(new Date()); // 维修验收时间


        // 4、更新任务表(状态)
        Task task = new Task();
        // 数据
        task.setOrderid(orderTable.getOrderid()); // 维修单ID(D+年月日+时分秒+6位随机数)
        task.setTaskstate("N"); // 维修任务状态(Y:进行中/N:已完成)

        // 验收不合格转平台客服
        int count = 0;
        count = operationCheckService.updateApplyCheckUnqualifiedAndChangeService(orderTable, applyCheck, task);

        // 返回数据
        if (count == 0) {
            returnData.setMsg("验收不合格转平台客服失败！");
        } else {
            // 查询数据
            UserAndUserServiceAndUserCustomer selectData = new UserAndUserServiceAndUserCustomer();
            selectData.setUserid(orderTable.getOrderserviceuserid()); // 查询
            selectData.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)

            // 查询数据
            List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectData(selectData);
            if (userList.size() != 0) {
                for (int i = 0; i < userList.size(); i++) {
                    WechatForm wechatForm = new WechatForm();
                    wechatForm.setPagenumber(-1);//不分页
                    wechatForm.setUserid(userList.get(i).getUserid());
                    List<WechatForm> wechatFormList = wechatFormService.selectByWechatForm(wechatForm); // 查询数据
                    if (wechatFormList.size() != 0) {
                        String openId = "";
                        String templateId = "";
                        String formId = "";
                        for (int j = 0; j < wechatFormList.size(); j++) {
                            Date createTime = wechatFormList.get(j).getCreatedate();
                            Date nowTime = new Date();
                            long cha = nowTime.getTime() - createTime.getTime();
                            if (cha / (1000 * 60 * 60 * 24) > 6) {
                                List<Integer> deleteList = new ArrayList<Integer>();
                                deleteList.add(wechatFormList.get(j).getId());
                                wechatFormList.remove(j);
                                wechatFormService.deleteByIdList(deleteList);
                            }
                        }
                        openId = wechatFormList.get(0).getOpenid();
                        templateId = "k6sflSt7bIhP2eW_UX-Fgpq8NG-_rEOyHUiBmMYunuE";
                        formId = wechatFormList.get(0).getFormid();

                        SimpleDateFormat dateFormate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                        String token = F_WechatRestful.getAccessToken(wechatService);
                        //String token = "15_UmIZU3I9v8a7awi1Wd-fhTNzaRP2qFujMRMtDgCmoZgxDDxMghRXg7GH57Fr_k28RCbJI5tznJO6tvH927dCRYIoARmaDBx4ZOjCQsZrjI59QfDHZ6YiYCeusDFYTirha23vtI-SnvFptzvTOHNiAIAZSY";
                        String jsonMsg = WechatFormUtil.makeRouteMessage(openId, templateId, formId, "", "#ff6600", "验收不合格", "验收不合格", dateFormate.format(applyCheck.getApplychecktime()), orderTable.getOrderid());
                        boolean result = WechatFormUtil.sendTemplateMessage(token, jsonMsg);
                        List<Integer> idList = new ArrayList<Integer>();
                        idList.add(wechatFormList.get(0).getId());
                        wechatFormService.deleteByIdList(idList);

                        logger.error("服务通知result" + result);
                        System.out.println("服务通知result" + result);

                    }
                }
            }
            returnData.setCount(count);
            returnData.setMsg("验收不合格转平台客服成功！");
        }

        return returnData;
    }


    /**
     * 验收不合格 最新的更改订单验收状态和不合格原因  (2019-01-20最新的文档)
     * <p>
     * 操作过程：
     * 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
     * 2、更新维修单表(状态)
     * 3、更新验收表(状态)
     * 4、更新任务表(状态)
     * <p>
     * 传递OrderTable实体
     * <p>
     * 需在OrderTable实体中添加版本信息(可以为空)
     * 需在OrderTable实体中添加当前操作人员ID
     *
     * @param orderTable
     * @return
     * @author ZY on 2018/09/17
     */
    @ResponseBody
    @RequestMapping("/updateApplyCheckUnqualifiedNew")
    public LayuiDataTemplet<OrderTable> updateApplyCheckUnqualifiedNew(@RequestBody OrderTable orderTable) {
        LayuiDataTemplet<OrderTable> returnData = new LayuiDataTemplet<OrderTable>(); // 返回数据
        returnData.setCode(0); // 默认为0
        returnData.setCount(0); // 数据的数量，默认为0
        returnData.setData(null); // 数据List，默认为null

        // 版本号不为空，则验证版本号
        try {
            if (orderTable.getVersion() != null && !orderTable.getVersion().isEmpty()) {
                // 前者大则返回一个正数，后者大返回一个负数，相等则返回0
                int compare = VersionCompare.compare(orderTable.getVersion(), Config.VERSION);
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

        // 当前操作人员ID
        String currentUserId = orderTable.getCurrentuserid();
        if (currentUserId == null || currentUserId.isEmpty()) {
            returnData.setMsg("当前操作人ID为空，无法转平台客服！");
            return returnData;
        }
        //不合格原因
        String applyCheckContent = orderTable.getApplycheckcontent();

        // 1、根据OrderTable实体中的维修单ID，获取对应的维修单数据
        OrderTable selectOrderTable = new OrderTable();
        selectOrderTable.setOrderid(orderTable.getOrderid());
        selectOrderTable.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)
        List<OrderTable> orderList = null;
        orderList = orderTableService.selectByOrderTable(selectOrderTable);
        if (orderList == null || orderList.size() == 0) {
            returnData.setMsg("找不到该维修单，无法转平台客服！");
            return returnData;
        } else {
            orderTable = orderList.get(0);
        }

        //查询该订单是否已经验收过
        ApplyCheckAndOrderTableAndUser applyCheckAndOrderTableAndUser = new ApplyCheckAndOrderTableAndUser();
        applyCheckAndOrderTableAndUser.setPagenumber(-1);//不分页
        applyCheckAndOrderTableAndUser.setOrderid(orderTable.getOrderid());//订单ID
        List<ApplyCheckAndOrderTableAndUser> list = null;
        list = applyCheckService.selectThreeTablesByUnionDataByTimeDesc(applyCheckAndOrderTableAndUser);
        if (!list.get(0).getApplycheckstate().equals("SQYS")) {
            returnData.setMsg("该维修单已经验收，请勿重复提交验收！");
            return returnData;
        }

        // 2、更新维修单表(状态) 不合格时重新让维修工维修只是增加一条不合格的记录
        orderTable.setOrderstate("JS"); // 维修状态(BJ:编辑/FB:发布/QD:抢单/JS:接收/WXTH:维修人员退回/KHQX:客户取消/QRQX:维修人员确认取消/SQYS:申请验收/YSHG:验收合格/YSBHG:验收不合格/GBDD:关闭订单)
        orderTable.setOrderupdateuserid(currentUserId); // 当前操作人员ID // 维修更新人ID(YHBG+UUID)
        orderTable.setOrdercheckallstate(null);
        orderTable.setOrderupdatetime(new Date()); // 维修更新时间

        // 3、更新验收表
        ApplyCheck applyCheck = new ApplyCheck();
        // 数据
        applyCheck.setOrderid(orderTable.getOrderid()); // 订单ID
        applyCheck.setApplycheckid(list.get(0).getApplycheckid()); // 验收ID
        applyCheck.setApplycheckstate("BHG"); // 维修验收状态(SQYS:申请验收)(HG:合格)((派单模式)GHWX:更换维修人员)((抢单模式)WTPD:委托平台派单/PTXT:平台协调)(ZDYS:自动验收)
        applyCheck.setApplycheckuserid(currentUserId); // 维修验收人员ID(YHBG+UUID)
        applyCheck.setApplycheckcontent(applyCheckContent);//不合格原因
        applyCheck.setApplychecktime(new Date()); // 维修验收时间


        // 验收不合格转平台客服
        int count = 0;
        count = operationCheckService.updateApplyCheckUnqualifiedNew(orderTable, applyCheck);

        // 返回数据
        if (count == 0) {
            returnData.setMsg("验收不合格失败！");
        } else {
            // 查询数据
            UserAndUserServiceAndUserCustomer selectData = new UserAndUserServiceAndUserCustomer();
            selectData.setUserid(orderTable.getOrderserviceuserid()); // 查询
            selectData.setPagenumber(-1); // 当前页数(如果不进行分页，该条数据默认为-1)

            // 查询数据
            List<UserAndUserServiceAndUserCustomer> userList = userService.selectThreeTablesBySelectData(selectData);
            if (userList.size() != 0) {
                for (int i = 0; i < userList.size(); i++) {
                    WechatForm wechatForm = new WechatForm();
                    wechatForm.setPagenumber(-1);//不分页
                    wechatForm.setUserid(userList.get(i).getUserid());
                    List<WechatForm> wechatFormList = wechatFormService.selectByWechatForm(wechatForm); // 查询数据
                    if (wechatFormList.size() != 0) {
                        String openId = "";
                        String templateId = "";
                        String formId = "";
                        for (int j = 0; j < wechatFormList.size(); j++) {
                            Date createTime = wechatFormList.get(j).getCreatedate();
                            Date nowTime = new Date();
                            long cha = nowTime.getTime() - createTime.getTime();
                            if (cha / (1000 * 60 * 60 * 24) > 6) {
                                List<Integer> deleteList = new ArrayList<Integer>();
                                deleteList.add(wechatFormList.get(j).getId());
                                wechatFormList.remove(j);
                                wechatFormService.deleteByIdList(deleteList);
                            }
                        }
                        openId = wechatFormList.get(0).getOpenid();
                        templateId = "k6sflSt7bIhP2eW_UX-Fgpq8NG-_rEOyHUiBmMYunuE";
                        formId = wechatFormList.get(0).getFormid();

                        SimpleDateFormat dateFormate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                        String token = F_WechatRestful.getAccessToken(wechatService);
                        //String token = "15_UmIZU3I9v8a7awi1Wd-fhTNzaRP2qFujMRMtDgCmoZgxDDxMghRXg7GH57Fr_k28RCbJI5tznJO6tvH927dCRYIoARmaDBx4ZOjCQsZrjI59QfDHZ6YiYCeusDFYTirha23vtI-SnvFptzvTOHNiAIAZSY";
                        String jsonMsg = WechatFormUtil.makeRouteMessage(openId, templateId, formId, "", "#ff6600", "验收不合格", applyCheck.getApplycheckcontent(), dateFormate.format(applyCheck.getApplychecktime()), orderTable.getOrderid());
                        boolean result = WechatFormUtil.sendTemplateMessage(token, jsonMsg);
                        List<Integer> idList = new ArrayList<Integer>();
                        idList.add(wechatFormList.get(0).getId());
                        wechatFormService.deleteByIdList(idList);

                        logger.error("服务通知result" + result);
                        System.out.println("服务通知result" + result);

                    }
                }
            }
            returnData.setCount(count);
            returnData.setMsg("验收不合格成功！");
        }

        return returnData;
    }


}

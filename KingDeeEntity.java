package wechat.entity;

/**
 *
 *金蝶相关的参数
 *
 *
 * @author ZY on 2019/03/19
*/
public class KingDeeEntity {

    //金蝶中项目的ID
    private int kingdeeorderid;

    //项目名称查询
    private String kingdeename;

    //项目编码
    private String kingdeeorderfnumber;

    public int getKingdeeorderid() {
        return kingdeeorderid;
    }

    public void setKingdeeorderid(int kingdeeorderid) {
        this.kingdeeorderid = kingdeeorderid;
    }

    public String getKingdeename() {
        return kingdeename;
    }

    public void setKingdeename(String kingdeename) {
        this.kingdeename = kingdeename;
    }

    public String getKingdeeorderfnumber() {
        return kingdeeorderfnumber;
    }

    public void setKingdeeorderfnumber(String kingdeeorderfnumber) {
        this.kingdeeorderfnumber = kingdeeorderfnumber;
    }
}

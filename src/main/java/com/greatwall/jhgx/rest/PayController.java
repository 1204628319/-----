package com.greatwall.jhgx.rest;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.greatwall.component.ccyl.common.utils.DateUtil;
import com.greatwall.jhgx.domain.Member;
import com.greatwall.jhgx.domain.PayOrder;
import com.greatwall.jhgx.domain.UploadImage;
import com.greatwall.jhgx.entity.CommonResponse;
import com.greatwall.jhgx.entity.PayStatusEnum;
import com.greatwall.jhgx.entity.Result;
import com.greatwall.jhgx.service.MemberService;
import com.greatwall.jhgx.service.PayOrderService;
import com.greatwall.jhgx.service.UploadImageService;
import com.greatwall.jhgx.util.CommonUtil;
import com.greatwall.jhgx.util.CurrencyUtils;
import com.greatwall.jhgx.util.HttpClientUtil;
import com.greatwall.jhgx.util.MD5Util;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sun.misc.BASE64Encoder;

import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

@Slf4j
@Api(tags = "支付")
@RestController
@RequestMapping("/alipayCreditPay/")
public class PayController {

    @Autowired
    private PayOrderService payOrderService;

    @Autowired
    private UploadImageService uploadImageService;

    @Autowired
    private MemberService memberService;

    /**
     * 支付url
     */
    @Value("${payConfig.scPay}")
    private String payConfigScPay;

    /**
     * 查询url
     */
    @Value("${payConfig.query}")
    private String payConfigQuery;

    /**
     * 上传照片url
     */
    @Value("${payConfig.uploadmerImage}")
    private String payConfigUploadmerImage;

    /**
     * 个人商户注册url
     */
    @Value("${payConfig.scMember}")
    private String payConfigScMember;

    /**
     * 个人商户查询url
     */
    @Value("${payConfig.scMemberQuery}")
    private String payConfigScMemberQuery;

    /**
     * 密钥
     */
    @Value("${payConfig.key}")
    private String payConfigKey;

    /**
     * 机构商户号
     */
    @Value("${payConfig.merchantId}")
    private String payConfigMerchantId;

    /**
     * 支付通道类型
     */
    @Value("${payConfig.agencyType}")
    private String payConfigAgencyType;


    /**
     * 费率
     */
    @Value("${payConfig.consRate}")
    private String payConfigConsRate;

    /**
     * 支付通知url
     */
    @Value("${payConfig.notifyUrl}")
    private String payConfigNotifyUrl;

    /**
     * 客户端公网ipv4地址
     */
    @Value("${payConfig.clientIp}")
    private String payConfigClientIp;

    @ApiOperation("testApi")
    @GetMapping(value = "/testApi")
    public Result testApi() {
        return Result.success("testApi");
    }

    @PostMapping(value = "/uploadImage")
    public Result uploadImage(@RequestParam("data") String data, @RequestParam("file") MultipartFile file) throws IOException {

        String suffix = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".") + 1);
        BASE64Encoder base64Encoder =new BASE64Encoder();
        String base64EncoderImg = file.getOriginalFilename()+","+ base64Encoder.encode(file.getBytes());

        UploadImage uploadImage = JSON.parseObject(data, UploadImage.class);
        uploadImage.setMerchantId(payConfigMerchantId);
        uploadImage.setImageType(suffix);
        uploadImage.setImage(base64EncoderImg);

        Hashtable<String,String> map=new Hashtable<String, String>();
        map.put("merchantId", uploadImage.getMerchantId());
        map.put("certId", uploadImage.getCertId());
        map.put("imageType", uploadImage.getImageType());
        map.put("imageDefinition", uploadImage.getImageDefinition());
        map.put("image", uploadImage.getImage());

        StringBuilder sb = CommonUtil.buildSign(map);
        System.out.println("params=" + sb.toString());
        map.put("sign", MD5Util.md5(sb.append("key=" + payConfigKey).toString()));

        try {
            //调用工具类进行上传
            String responseMsg = HttpClientUtil.postSSL(payConfigUploadmerImage, map);
            log.debug("responseMsg={}", responseMsg);

            CommonResponse commonResponse = JSON.parseObject(responseMsg, CommonResponse.class); //此处需要测试
            if ("00".equals(commonResponse.getResCode())) {
                // 入库
                boolean saveResult = uploadImageService.save(uploadImage);
                log.info("saveResult={}", saveResult);
                return Result.success("上传照片成功");
            } else {
                return Result.fail("上传照片失败" + commonResponse.getResMsg());
            }
        } catch (Exception e) {
            log.error("上传照片失败", e);
            return Result.fail("上传照片失败");
        }
    }

    @PostMapping(value = "selectUploadImage")
    public Result selectUploadImage(@RequestBody UploadImage uploadImage) {
        if (StringUtils.isEmpty(uploadImage.getCertId())) {
            return Result.success("");
        }

        QueryWrapper<UploadImage> queryWrapper = new QueryWrapper();
        queryWrapper.eq("CERT_ID", uploadImage.getCertId()).select("CERT_ID", "IMAGE_DEFINITION", "CREATE_TIME");
        queryWrapper.orderByAsc("IMAGE_DEFINITION");
        List<UploadImage> list  = uploadImageService.list(queryWrapper);
        for (UploadImage vo : list) {
            if ("1".equals(vo.getImageDefinition())) {
                vo.setImageDefinitionName("身份证正面");
            } else if ("2".equals(vo.getImageDefinition())) {
                vo.setImageDefinitionName("身份证反面");
            }else if ("3".equals(vo.getImageDefinition())) {
                vo.setImageDefinitionName("结算卡正面");
            }else if ("4".equals(vo.getImageDefinition())) {
                vo.setImageDefinitionName("手持结算卡身份证");
            } else {
                vo.setImageDefinitionName(vo.getImageDefinition());
            }
        }

        return Result.success("查询成功", list);
    }

    @PostMapping(value = "/scMember")
    public Result scMember(@RequestBody Member member) {

        member.setMerchantId(payConfigMerchantId);
        member.setAgencyType(payConfigAgencyType);
        member.setMerOrderId(getMerOrderId());
        member.setConsFee("0");
        member.setSignStatus("notSign");
        member.setConsRate(payConfigConsRate);

        Hashtable<String,String> map=new Hashtable<String, String>();
        map.put("merchantId", member.getMerchantId());
        map.put("agencyType", member.getAgencyType());
        map.put("realName", member.getRealName());
        map.put("certId", member.getCertId());
        map.put("merName", member.getMerName());
        map.put("merState", member.getMerState());
        map.put("merCity", member.getMerCity());
        map.put("merAddress", member.getMerAddress());
        map.put("merOrderId", member.getMerOrderId());
        map.put("consRate", member.getConsRate());
        map.put("consFee", member.getConsFee());
        map.put("settleCardNo", member.getSettleCardNo());
        map.put("settleMobile", member.getSettleMobile());

        StringBuilder sb = CommonUtil.buildSign(map);
        member.setRequestMsg(JSON.toJSONString(map));
        map.put("sign", MD5Util.md5(sb.append("key=" + payConfigKey).toString()));

        try {
            //调用工具类进行商户注册
            String responseMsg = HttpClientUtil.postSSL(payConfigScMember, map);
            log.debug("responseMsg={}", responseMsg);
            member.setResponseMsg(responseMsg);

            CommonResponse commonResponse = JSON.parseObject(responseMsg, CommonResponse.class);
            if ("00".equals(commonResponse.getResCode())) {
                member.setSignStatus("signed");
                return Result.success("进件成功");
            } else if("01".equals(commonResponse.getResCode())) {
                member.setSignStatus("signing");
                return Result.fail("处理中" + commonResponse.getResMsg());
            } else if("02".equals(commonResponse.getResCode())) {
                member.setSignStatus("refused");
                return Result.fail("已拒绝" + commonResponse.getResMsg());
            } else {
                member.setSignStatus("fail");
                return Result.fail("注册失败" + commonResponse.getResMsg());
            }
        } catch (Exception e) {
            member.setSignStatus("abnormal");
            log.error("注册异常，请稍后进行查询", e);
            return Result.fail("注册异常，请稍后进行查询");
        } finally {
            boolean saveResult = memberService.save(member);
            log.info("saveResult={}", saveResult);
        }
    }

    @PostMapping(value = "selectScMember")
    public Result selectScMember(@RequestBody Member member) {
        if (StringUtils.isEmpty(member.getCertId())) {
            return Result.success("");
        }

        QueryWrapper<Member> queryWrapper = new QueryWrapper();
        queryWrapper.eq("CERT_ID", member.getCertId()).select("ID","CERT_ID");
        List<Member> list  = memberService.list(queryWrapper);
        if (CollectionUtils.isEmpty(list)) {
            return Result.fail("身份证号在本系统不存在");
        }

        // 调用接口获取实际的状态
        String msg = "";
        for (Member vo : list) {
            msg = msg + "," + syncMember(vo);
        }

        queryWrapper = new QueryWrapper();
        queryWrapper.eq("CERT_ID", member.getCertId()).select("ID", "CERT_ID", "SETTLE_MOBILE", "CONS_RATE", "SIGN_STATUS", "CREATE_TIME");
        queryWrapper.orderByDesc("CREATE_TIME");
        list  = memberService.list(queryWrapper);
        for (Member vo : list) {
            if ("notSign".equals(vo.getSignStatus())) {
                vo.setSignStatusName("未开始进件");
            } else if ("signing".equals(vo.getSignStatus())) {
                vo.setSignStatusName("处理中");
            } else if ("signed".equals(vo.getSignStatus())) {
                vo.setSignStatusName("进件成功");
            } else if ("refused".equals(vo.getSignStatus())) {
                vo.setSignStatusName("已拒绝");
            } else if ("fail".equals(vo.getSignStatus())) {
                vo.setSignStatusName("失败");
            } else if ("abnormal".equals(vo.getSignStatus())) {
                vo.setSignStatusName("异常");
            } else {

            }
        }

        return Result.success("查询成功", list);
    }

    @PostMapping(value = "/pay")
    public Result pay(@RequestBody PayOrder payOrder) {

        //校验入参
        if (StringUtils.isEmpty(payOrder.getAuthCode())) {
            return Result.fail("请输入付款码");
        }
        if (StringUtils.isEmpty(payOrder.getOrderAmtByYuan())) {
            return Result.fail("请输入金额");
        }

        // 获取进件人
        Member member = getMinDayAmtTotalMember();
        if (member == null) {
            return Result.fail("没有可使用的进件人");
        }

        String orderAmt = Integer.toString(CurrencyUtils.parseCNY2Cent(Double.valueOf(payOrder.getOrderAmtByYuan())));
        payOrder.setOrderAmt(orderAmt);

        payOrder.setAgencyType(payConfigAgencyType);
        payOrder.setPayType("99");
        payOrder.setPayStatus(PayStatusEnum.notPay.getValue());
        payOrder.setCertId(member.getCertId());
        payOrder.setMobile(member.getSettleMobile());
        payOrder.setCardNo(member.getSettleCardNo());
        payOrder.setMerchantId(payConfigMerchantId);
        payOrder.setMerOrderId(getMerOrderId());
        payOrder.setTransTime(DateUtil.date2String(new Date(), DateUtil.PATTERN_NO_COLON));
        payOrder.setPayDate(DateUtil.currentTimestamp2String(DateUtil.PATTERN_DATE));

        //拼接支付参数
        Hashtable<String,String> map = new Hashtable<>();
        map.put("agencyType", payOrder.getAgencyType());
        map.put("merchantId", payOrder.getMerchantId());
        map.put("certId", payOrder.getCertId());
        map.put("merOrderId", payOrder.getMerOrderId());
        map.put("orderAmt", payOrder.getOrderAmt());
        map.put("cardNo", payOrder.getCardNo());
        map.put("mobile", payOrder.getMobile());
        map.put("notifyUrl", payConfigNotifyUrl);
        map.put("transTime", payOrder.getTransTime());
        map.put("payType", payOrder.getPayType());
        map.put("clientIp", payConfigClientIp);
        map.put("authCode", payOrder.getAuthCode());

        StringBuilder sb = CommonUtil.buildSign(map);
        map.put("sign", MD5Util.md5(sb.append("key=" + payConfigKey).toString()));

        //支付参数入库
        payOrder.setRequestMsg(JSON.toJSONString(map));
        boolean saveResult = payOrderService.save(payOrder);
        log.info("saveResult={}", saveResult);

        PayOrder payOrderUpdate = new PayOrder();
        payOrderUpdate.setId(payOrder.getId());
        try {
            //调用工具类进行支付
            String responseMsg = HttpClientUtil.postSSL(payConfigScPay, map);
            log.debug("responseMsg={}", responseMsg);
            payOrderUpdate.setResponseMsg(responseMsg);

            CommonResponse commonResponse = JSON.parseObject(responseMsg, CommonResponse.class);
            if ("00".equals(commonResponse.getResCode())) {
                payOrderUpdate.setPayStatus("payed");
                return Result.success("支付成功");
            } else if ("01".equals(commonResponse.getResCode())) {
                payOrderUpdate.setPayStatus("paying");
                return Result.fail("支付处理中，请稍后查询支付结果");
            } else {
                payOrderUpdate.setPayStatus("fail");
                return Result.fail("支付失败" + commonResponse.getResMsg());
            }
        } catch (Exception e) {
            log.error("支付异常，请稍后查询支付结果", e);
            payOrderUpdate.setPayStatus("abnormal");
            return Result.fail("支付异常，请稍后查询支付结果");
        } finally {
            //更新订单状态
            boolean updateResult = payOrderService.updateById(payOrderUpdate);
            log.info("updateResult={}", updateResult);
        }
    }

    @ApiOperation("查询支付结果")
    @PostMapping(value = "selectPayResult")
    public Result selectPayResult(@RequestBody PayOrder payOrder) {
        if(StringUtils.isEmpty(payOrder.getAuthCode())) {
            return Result.success("");
        }

        QueryWrapper<PayOrder> queryWrapper = new QueryWrapper();
        queryWrapper.eq("auth_code", payOrder.getAuthCode());
        queryWrapper.orderByDesc("create_time");
        List<PayOrder> payOrderList = payOrderService.list(queryWrapper);
        if (CollectionUtils.isEmpty(payOrderList)) {
            return Result.fail("该付款码在本系统找不到");
        }

        String msg = null;
        for (PayOrder vo : payOrderList) {
            msg = sync(vo);
        }

        payOrderList = payOrderService.selectOrder(payOrder.getAuthCode());
        for (PayOrder vo : payOrderList) {
            try{
                String data = DateUtil.date2String(DateUtil.string2Date(vo.getTransTime(), DateUtil.PATTERN_NO_COLON), DateUtil.PATTERN_MS);
                vo.setTransTime(data);
            } catch (Exception e){
                log.error("时间转换错误", e);
            }

            String orderAmtByYuan = Double.toString(CurrencyUtils.parseCent2CNY(Integer.valueOf(vo.getOrderAmt())));
            vo.setOrderAmtByYuan(orderAmtByYuan);

            if (PayStatusEnum.notPay.getValue().equals(vo.getPayStatus())) {
                vo.setPayStatusName(PayStatusEnum.notPay.getName());
            } else if (PayStatusEnum.paying.getValue().equals(vo.getPayStatus())) {
                vo.setPayStatusName(PayStatusEnum.paying.getName());
            }else if (PayStatusEnum.payed.getValue().equals(vo.getPayStatus())) {
                vo.setPayStatusName(PayStatusEnum.payed.getName());
            }else if (PayStatusEnum.fail.getValue().equals(vo.getPayStatus())) {
                vo.setPayStatusName(PayStatusEnum.fail.getName());
            }else if (PayStatusEnum.abnormal.getValue().equals(vo.getPayStatus())) {
                vo.setPayStatusName(PayStatusEnum.abnormal.getName());
            } else {
                vo.setPayStatusName(vo.getPayStatus());
            }
        }

        if (StringUtils.isNotEmpty(msg)) {
            return Result.success(msg, payOrderList);
        } else {
            return Result.success("查询成功", payOrderList);
        }
    }

    /**
     * 查询出当日金额最小的已注册成功进件人
     * @return
     */
    private Member getMinDayAmtTotalMember() {
        return memberService.selectMinDayOrderAmtMember(DateUtil.currentTimestamp2String(DateUtil.PATTERN_DATE));
    }

    private String syncMember(Member member) {
        Hashtable<String,String> map=new Hashtable<String, String>();
        map.put("agencyType", "wzb");
        map.put("merchantId", payConfigMerchantId);
        map.put("certId", member.getCertId());
        StringBuilder sb = CommonUtil.buildSign(map);
        map.put("sign", MD5Util.md5(sb.append("key=" + payConfigKey).toString()));

        Member memberUpdate = new Member();
        memberUpdate.setId(member.getId());
        try {
            String responseMsg = HttpClientUtil.postSSL(payConfigScMemberQuery, map);
            memberUpdate.setQueryResultMsg(responseMsg);
            CommonResponse commonResponse = JSON.parseObject(responseMsg, CommonResponse.class);
            if ("00".equals(commonResponse.getResCode())) {
                memberUpdate.setSignStatus("signed");
            } else if("01".equals(commonResponse.getResCode())) {
                memberUpdate.setSignStatus("signing");
            } else if("02".equals(commonResponse.getResCode())) {
                memberUpdate.setSignStatus("refused");
            } else {
                memberUpdate.setSignStatus("fail");
            }
        } catch (Exception e) {
            log.error("查询订单状态异常", e);
        } finally {
            //更新订单状态
            if (StringUtils.isNotEmpty(memberUpdate.getSignStatus())) {
                boolean updateResult = memberService.updateById(memberUpdate);
                log.info("updateResult={}", updateResult);
            }
        }
        return null;
    }

    private String sync(PayOrder payOrder) {
        Hashtable<String,String> map=new Hashtable<String, String>();
        map.put("agencyType", "wzb");
        map.put("merchantId", payConfigMerchantId);
        map.put("merOrderId", payOrder.getMerOrderId());
        map.put("transTime", DateUtil.date2String(new Date(), DateUtil.PATTERN_NO_COLON));
        StringBuilder sb = CommonUtil.buildSign(map);
        map.put("sign", MD5Util.md5(sb.append("key=" + payConfigKey).toString()));

        PayOrder payOrderUpdate = new PayOrder();
        payOrderUpdate.setId(payOrder.getId());
        try {
            String responseMsg = HttpClientUtil.postSSL(payConfigQuery, map);
            payOrderUpdate.setQueryResultMsg(responseMsg);
            CommonResponse commonResponse = JSON.parseObject(responseMsg, CommonResponse.class); //此处需要测试
            if ("00".equals(commonResponse.getResCode())) {
                payOrderUpdate.setPayStatus("payed");
            } else if ("01".equals(commonResponse.getResCode())) {
                payOrderUpdate.setPayStatus("paying");
            } else if ("FQ".equals(commonResponse.getResCode())) {
                payOrderUpdate.setPayStatus(commonResponse.getResMsg());
            } else {
                payOrderUpdate.setPayStatus("fail");
            }
        } catch (Exception e) {
            log.error("查询订单状态异常", e);
        } finally {
            //更新订单状态
            if (StringUtils.isNotEmpty(payOrderUpdate.getPayStatus())) {
                boolean updateResult = payOrderService.updateById(payOrderUpdate);
                log.info("updateResult={}", updateResult);
            }
        }
        return null;
    }

    private String getMerOrderId() {
        String merOrderId = "P" + payConfigMerchantId + System.currentTimeMillis() + (int)(1+Math.random()*(100-1+1));
        if (merOrderId.length() > 35) {
            return merOrderId.substring(0, 34);
        } else {
            return merOrderId;
        }
    }
}

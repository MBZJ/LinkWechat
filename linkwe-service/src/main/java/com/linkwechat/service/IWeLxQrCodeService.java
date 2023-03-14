package com.linkwechat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.pagehelper.PageInfo;
import com.linkwechat.domain.WeLxQrCode;
import com.linkwechat.domain.qr.query.WeLxQrAddQuery;
import com.linkwechat.domain.qr.query.WeLxQrCodeListQuery;
import com.linkwechat.domain.qr.query.WxLxQrQuery;
import com.linkwechat.domain.qr.vo.*;

import java.util.List;

/**
 * 拉新活码信息表(WeLxQrCode)
 *
 * @author danmo
 * @since 2023-03-07 14:59:35
 */
public interface IWeLxQrCodeService extends IService<WeLxQrCode> {

    /**
     * 新增拉新活码
     * @param weQrAddQuery 入参
     */
    Long addQrCode(WeLxQrAddQuery weQrAddQuery);

    /**
     * 生成拉新二维码
     * @param id
     * @return
     */
    WeLxQrAddVo generateQrCode(Long id);

    /**
     * 修改拉新活码
     * @param query
     */
    void updateQrCode(WeLxQrAddQuery query);

    /**
     * 删除拉新活码
     * @param ids 活码ID
     */
    void delQrCode(List<Long> ids);

    /**
     * 查询拉新活码详情
     * @param id 活码ID
     * @return
     */
    WeLxQrCodeDetailVo getQrDetail(Long id);

    WeLxQrCodeDetailVo getQrDetail(Long id, Boolean isNeedName);


    WeLxQrCodeDetailVo getQrDetailByState(String state);

    /**
     * 获取拉新活码列表
     * @param query
     * @return
     */
    PageInfo<WeLxQrCodeListVo> getQrCodeList(WeLxQrCodeListQuery query);

    /**
     * 统计拉新活码折线图
     * @param query
     * @return
     */
    WeLxQrCodeLineVo getWeQrCodeLineStatistics(WeLxQrCodeListQuery query);

    /**
     * 统计拉新活码列表
     * @param query
     * @return
     */
    List<WeLxQrCodeSheetVo> getWeQrCodeListStatistics(WeLxQrCodeListQuery query);

    /**
     * 获取企微活码
     * @param query
     * @return
     */
    WxLxQrCodeVo getQrcode(WxLxQrQuery query);
}

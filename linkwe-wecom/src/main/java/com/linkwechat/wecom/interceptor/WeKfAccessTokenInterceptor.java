package com.linkwechat.wecom.interceptor;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.dtflys.forest.exceptions.ForestRuntimeException;
import com.dtflys.forest.http.ForestRequest;
import com.dtflys.forest.http.ForestResponse;
import com.dtflys.forest.interceptor.Interceptor;
import com.google.common.collect.Lists;
import com.linkwechat.common.enums.WeErrorCodeEnum;
import com.linkwechat.common.utils.StringUtils;
import com.linkwechat.common.utils.spring.SpringUtils;
import com.linkwechat.domain.wecom.vo.WeResultVo;
import com.linkwechat.wecom.service.IQwAccessTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @description: 客服token拦截器
 * @author: danmo
 * @create: 2022-01-23 22:36
 **/
@Slf4j
@Component
public class WeKfAccessTokenInterceptor extends WeForestInterceptor implements Interceptor<WeResultVo> {

    /**
     * 该方法在请求发送之前被调用, 若返回false则不会继续发送请求
     */
    @Override
    public boolean beforeExecute(ForestRequest request) {
        if (iQwAccessTokenService == null) {
            iQwAccessTokenService = SpringUtils.getBean(IQwAccessTokenService.class);
        }
        String token = iQwAccessTokenService.findKfAccessToken(getCorpId(request));
        request.replaceOrAddQuery("access_token", token);
        return true;
    }


    /**
     * 请求发送失败时被调用
     *
     * @param e
     * @param forestRequest
     * @param forestResponse
     */
    @Override
    public void onError(ForestRuntimeException e, ForestRequest forestRequest, ForestResponse forestResponse) {
        log.error("url:{},------params:{},----------result:" + forestRequest.getUrl(), JSONObject.toJSONString(forestRequest.getArguments()), forestResponse.getContent());
        if (StringUtils.isNotEmpty(forestResponse.getContent())) {
            throw new ForestRuntimeException(forestResponse.getContent());
        } else {
            throw new ForestRuntimeException("网络请求超时");
        }
    }


    /**
     * 请求成功调用(微信端错误异常统一处理)
     *
     * @param resultVo
     * @param forestRequest
     * @param forestResponse
     */
    @Override
    public void onSuccess(WeResultVo resultVo, ForestRequest forestRequest, ForestResponse forestResponse) {
        log.info("url:{},result:{}", forestRequest.getUrl(), forestResponse.getContent());
    }

    /**
     * 请求重试
     *
     * @param request  请求
     * @param response 返回值
     */
    @Override
    public void onRetry(ForestRequest request, ForestResponse response) {
        log.info("url:{}, query:{},params:{}, 重试原因:{}, 当前重试次数:{}", request.getUrl(), request.getQueryString(), JSONObject.toJSONString(request.getArguments()), response.getContent(), request.getCurrentRetryCount());
        WeResultVo weResultVo = JSONUtil.toBean(response.getContent(), WeResultVo.class);
        //当错误码符合重置token时，刷新token
        if (!ObjectUtil.equal(WeErrorCodeEnum.ERROR_CODE_OWE_1.getErrorCode(), weResultVo.getErrCode())
                && Lists.newArrayList(errorCodeRetry).contains(weResultVo.getErrCode())) {

            String corpId = getCorpId(request);
            //删除缓存
            iQwAccessTokenService.removeKfAccessToken(corpId);
            //重新查询token
            String token = iQwAccessTokenService.findKfAccessToken(corpId);

            request.replaceOrAddQuery("access_token", token);
        }
    }
}

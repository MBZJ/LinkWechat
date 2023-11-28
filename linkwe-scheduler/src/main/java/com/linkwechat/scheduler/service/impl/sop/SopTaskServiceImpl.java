package com.linkwechat.scheduler.service.impl.sop;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.setting.SettingUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkwechat.common.constant.Constants;
import com.linkwechat.common.context.SecurityContextHolder;
import com.linkwechat.common.core.domain.model.LoginUser;
import com.linkwechat.common.enums.SopType;
import com.linkwechat.common.enums.TrackState;
import com.linkwechat.common.enums.strategiccrowd.CorpAddStateEnum;
import com.linkwechat.common.enums.strategiccrowd.CrowdSwipeTypeEnum;
import com.linkwechat.common.enums.strategiccrowd.CustomerAttributesEnum;
import com.linkwechat.common.utils.DateUtils;
import com.linkwechat.common.utils.MapUtils;
import com.linkwechat.common.utils.spring.SpringUtils;
import com.linkwechat.domain.WeCustomer;
import com.linkwechat.domain.WeGroup;
import com.linkwechat.domain.WeSopChange;
import com.linkwechat.domain.customer.query.WeCustomersQuery;
import com.linkwechat.domain.customer.vo.WeCustomersVo;
import com.linkwechat.domain.groupchat.vo.LinkGroupChatListVo;
import com.linkwechat.domain.groupmsg.query.WeAddGroupMessageQuery;
import com.linkwechat.domain.sop.WeSopBase;
import com.linkwechat.domain.sop.dto.WeSopBaseDto;
import com.linkwechat.domain.sop.vo.WeSopExecuteConditVo;
import com.linkwechat.domain.strategic.crowd.WeStrategicCrowdSwipe;
import com.linkwechat.scheduler.service.AbstractCrowdService;
import com.linkwechat.scheduler.service.SopTaskService;
import com.linkwechat.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.SetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SopTaskServiceImpl implements SopTaskService {

    @Autowired
    private IWeSopBaseService iWeSopBaseService;

    @Autowired
    private IWeCustomerService iWeCustomerService;






    @Override
    public void createOrUpdateSop(WeSopBaseDto weSopBaseDto) {
        if (null != weSopBaseDto && weSopBaseDto.getLoginUser() != null) {
            LoginUser loginUser = weSopBaseDto.getLoginUser();
            SecurityContextHolder.setCorpId(loginUser.getCorpId());
            SecurityContextHolder.setUserName(loginUser.getUserName());
            SecurityContextHolder.setUserType(loginUser.getUserType());

            boolean isCreateOrUpdate = weSopBaseDto.isCreateOrUpdate();

            WeSopBase weSopBase = iWeSopBaseService.getById(weSopBaseDto.getSopBaseId());

            if (null != weSopBase) {



                    if (weSopBase.getBaseType() == 1) { //客户sop
                        //构建客户sop执行计划
                        iWeSopBaseService.builderExecuteCustomerSopPlan(weSopBase, builderExecuteWeCustomer(weSopBase), isCreateOrUpdate, false);
                    }else if (weSopBase.getBaseType() == 2) { //客群sop

                        Set<String> executeWeUserIds
                                = iWeSopBaseService.builderExecuteWeUserIds(weSopBase.getExecuteWeUser());
                    if (CollectionUtil.isNotEmpty(executeWeUserIds)) {
                        //构建客群sop执行计划
                        iWeSopBaseService.builderExecuteGroupSopPlan(weSopBase
                                , iWeSopBaseService.builderExecuteGroup(weSopBase,(WeSopExecuteConditVo) weSopBase.getExecuteCustomerOrGroup(), executeWeUserIds), isCreateOrUpdate,false);

                    }

                }

            }

        }
    }

    @Override
    public void builderNewWeCustomer(WeCustomer weCustomer) {
        List<WeCustomersVo> weCustomerList = iWeCustomerService.findWeCustomerList(WeCustomersQuery.builder()
                .externalUserid(weCustomer.getExternalUserid())
                .firstUserId(weCustomer.getAddUserId())
                .delFlag(Constants.COMMON_STATE)
                .build(), null);


        if(CollectionUtil.isNotEmpty(weCustomerList)){
            WeCustomersVo weCustomersVo = weCustomerList.stream().findFirst().get();

            if(null != weCustomersVo){
                //获取执行中的新客sop
                List<WeSopBase> weSopBases = iWeSopBaseService.list(new LambdaQueryWrapper<WeSopBase>()
                        .eq(WeSopBase::getBusinessType, SopType.SOP_TYPE_XK.getSopKey())
                        .eq(WeSopBase::getDelFlag, Constants.COMMON_STATE)
                        .eq(WeSopBase::getSopState, 1));
                if(CollectionUtil.isNotEmpty(weSopBases)){
                    weSopBases.stream().forEach(weSopBase -> {



                        Map<String, List<WeCustomersVo>> stringListMap
                                = builderExecuteWeCustomer(weSopBase);

                        if(CollectionUtil.isNotEmpty(stringListMap)){
                            List<WeCustomersVo> weCustomers = stringListMap.get(weCustomer.getAddUserId());

                            if(CollectionUtil.isNotEmpty(weCustomers)){

                                weCustomers.stream().forEach(k->{

                                    if(k.getExternalUserid().equals(weCustomer.getExternalUserid())){
                                        //加入新客sop
                                        iWeSopBaseService.builderExecuteCustomerSopPlan(weSopBase,
                                                MapUtil.builder(weCustomersVo.getFirstUserId(),ListUtil.list(false, weCustomersVo)).build()
                                                , false, true);
                                    }

                                });

                            }

                        }




                    });

                }
            }

        }



    }

    @Override
    public void builderNewGroup(LinkGroupChatListVo linkGroupChatListVo) {


        //获取执行中的新群sop
        List<WeSopBase> weSopBases = iWeSopBaseService.list(new LambdaQueryWrapper<WeSopBase>()
                .eq(WeSopBase::getBusinessType,SopType.SOP_TYPE_XQPY.getSopKey())
                .eq(WeSopBase::getDelFlag, Constants.COMMON_STATE)
                .eq(WeSopBase::getSopState, 1));

        if(CollectionUtil.isNotEmpty(weSopBases)){
            weSopBases.stream().forEach(weSopBase -> {


                //判断该群符不符合当前sop的条件
                Map<String, List<LinkGroupChatListVo>> groupListMap
                        = iWeSopBaseService.builderExecuteGroup(weSopBase, (WeSopExecuteConditVo) weSopBase.getExecuteCustomerOrGroup(),
                        CollUtil.newHashSet(linkGroupChatListVo.getOwner()));


                if(CollectionUtil.isNotEmpty(groupListMap)){
                    List<LinkGroupChatListVo> linkGroupChatListVos
                            = groupListMap.get(linkGroupChatListVo.getOwner());

                    if(CollectionUtil.isNotEmpty(linkGroupChatListVos)){

                        linkGroupChatListVos.stream().forEach(k->{

                            if(k.getChatId().equals(linkGroupChatListVo.getChatId())){
                                //构建新群sop
                                iWeSopBaseService.builderExecuteGroupSopPlan(weSopBase
                                        ,
                                        MapUtil.builder(linkGroupChatListVo.getOwner(),ListUtil.list(false, linkGroupChatListVo)).build()
                                        , true,true);
                            }

                        });

                    }




                }




            });







        }

    }

    @Override
    public void handleChangeSop(WeSopChange weSopChange) {


//        List<WeCustomer> weCustomers = iWeCustomerService.list(new LambdaQueryWrapper<WeCustomer>()
//                .eq(WeCustomer::getExternalUserid, weSopChange.getExternalUserid())
//                .eq(WeCustomer::getAddUserId, weSopChange.getAddUserId()));

        List<WeCustomersVo> weCustomerList = iWeCustomerService.findWeCustomerList(WeCustomersQuery.builder()
                .firstUserId(weSopChange.getAddUserId())
                .externalUserid(weSopChange.getExternalUserid())
                .build(), null);


        if(CollectionUtil.isNotEmpty(weCustomerList)){
            WeSopBase weSopBase
                    = iWeSopBaseService.getById(weSopChange.getSopBaseId());
            if(weSopBase != null && weSopBase.getBaseType().equals(1)){
                //构建转入sop的计划
                iWeSopBaseService.builderExecuteCustomerSopPlan(weSopBase,
                        weCustomerList.stream().collect(Collectors.groupingBy(WeCustomersVo::getFirstUserId)),
                        false, true);

            }
        }


    }



    //构建生效客户 isSelectYdCustomer(是否查询昨日新增客户,只针对新客sop，新加入的客户) true查询 false不查询
    private Map<String, List<WeCustomersVo>> builderExecuteWeCustomer(WeSopBase weSopBase) {

        List<WeCustomersVo> weCustomersVoList=new ArrayList<>();

        if(null != weSopBase){
            //全部客户
            if(new Integer(0).equals(weSopBase.getScopeType())){
                if (!weSopBase.getBusinessType().equals(SopType.SOP_TYPE_XK.getSopKey())){
                    weCustomersVoList=iWeCustomerService.findLimitWeCustomerList();
                }
            //按照条件筛选部分客户
            }else if(new Integer(1).equals(weSopBase.getScopeType())){
                //不等于新客sop，新客sop中的客户来源为新加入的客户
                if (!weSopBase.getBusinessType().equals(SopType.SOP_TYPE_XK.getSopKey())){
                    WeCustomersQuery weCustomersQuery = weSopBase.getWeCustomersQuery();
                    if(null != weCustomersQuery){
                        weCustomersQuery.setDelFlag(Constants.COMMON_STATE);
                        weCustomersVoList=iWeCustomerService.findWeCustomerList(weCustomersQuery, null);
                    }

                }
            }
        }

        return weCustomersVoList.stream().collect(Collectors.groupingBy(WeCustomersVo::getFirstUserId));





//
//        if (Objects.isNull(weSopBase.getExecuteCustomerOrGroup()) && CollectionUtil.isEmpty(weSopBase.getExecuteCustomerSwipe())) {//获取全部客户
//
//            weCustomerList = iWeCustomerService.list(
//                    new LambdaQueryWrapper<WeCustomer>()
//                            .in(WeCustomer::getAddUserId, executeWeUserIds)
//                            .ne(WeCustomer::getTrackState, TrackState.STATE_YLS.getType())
//                            .eq(WeCustomer::getDelFlag, Constants.COMMON_STATE)
//            );
//
//
//        } else if (CollectionUtil.isNotEmpty(weSopBase.getExecuteCustomerSwipe())) { //true客户属性跟进动态选择
//
//            weCustomerList = filterCustomers(weSopBase.getExecuteCustomerSwipe(), executeWeUserIds);
//
//
//
//        } else if (Objects.nonNull(weSopBase.getExecuteCustomerOrGroup()
//                .getCrowdAttribute()) && weSopBase.getExecuteCustomerOrGroup().getCrowdAttribute().isChange()) {//跟据人群获取客户
//            List<String> crowdIds = weSopBase.getExecuteCustomerOrGroup().getCrowdAttribute().getCrowdIds();
//            if (CollectionUtil.isNotEmpty(crowdIds)) {
//               List<WeCustomer> crowdCustomerList = iWeStrategicCrowdService.getCustomerListByCrowdIds(crowdIds);
//
//                if(CollectionUtil.isNotEmpty(crowdCustomerList)&&CollectionUtil.isNotEmpty(executeWeUserIds)){
//                    weCustomerList=crowdCustomerList.stream().filter(weCustomer -> executeWeUserIds.contains(weCustomer.getAddUserId())).collect(Collectors.toList());
//                }
//
//            }
//        }
//
//             if(CollectionUtil.isNotEmpty(weCustomerList)) {
//
//                 //如果是新客sop的话获取当天的客户构建执行计划(筛选出前一天)
//                 //如果是新客sop获取新客sop创建以后的时间
//                 if (weSopBase.getBusinessType().equals(SopType.SOP_TYPE_XK.getSopKey())) {
//                     if (isSelectYdCustomer) {
////                         weCustomerList = weCustomerList.stream().filter(weCustomer ->
////                                 DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, DateUtils.daysAgoOrAfter(new Date(), -1)).equals(
////                                         DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, weCustomer.getAddTime())
////                                 )).collect(Collectors.toList());
//
//
//
//                         weCustomerList =  weCustomerList.stream().filter(weCustomer ->
//
//                                 DateUtils.parseDate(DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, weSopBase.getCreateTime())).getTime()
//                                         <= DateUtils.parseDate(DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, weCustomer.getAddTime())).getTime()
//
//                         ).collect(Collectors.toList());
//
//                     }
//
//                 }
//                 return weCustomerList.stream().collect(Collectors.groupingBy(WeCustomer::getAddUserId));
//             }

//        return new HashMap();

    }



    //动态筛选客户(与牵扯到人群相关的计算)
    private List<WeCustomer>  filterCustomers(List<WeStrategicCrowdSwipe>  executeCustomerSwipes, Set<String> executeWeUserIds){
        List<WeCustomer> weCustomerList = new ArrayList<>();

        if(CollectionUtil.isEmpty(executeWeUserIds)){
            return weCustomerList;
        }

        try { //WeStrategicCrowdSwipe
            for (WeStrategicCrowdSwipe crowdSwipe : executeCustomerSwipes) {
                CrowdSwipeTypeEnum crowdSwipeTypeEnum = CrowdSwipeTypeEnum.parseEnum(crowdSwipe.getSwipType());
                if (crowdSwipeTypeEnum == null) {
                    continue;
                } else{
                    if("0".equals(crowdSwipe.getCode())){//渠道
                        crowdSwipe.setCode(CorpAddStateEnum.CORP_STATE.getCode().toString());
                    }

                }
                List<WeCustomer> calculate = SpringUtils.getBean(crowdSwipeTypeEnum.getMethod(), AbstractCrowdService.class).calculate(crowdSwipe);
                if (CollectionUtil.isEmpty(calculate)) {
                    continue;
                }
                List<Long> calculateCustomerIdList = calculate.parallelStream().map(WeCustomer::getId).collect(Collectors.toList());
                if (CollectionUtil.isNotEmpty(weCustomerList)) {
                    if (crowdSwipe.getAndOr() == 1) {
                        weCustomerList=weCustomerList.stream().filter(weCustomer -> calculateCustomerIdList.contains(weCustomer.getId())).collect(Collectors.toList());
                    } else if (crowdSwipe.getAndOr() == 2) {
                        weCustomerList.addAll(calculate);
                    }
                } else {
                    weCustomerList.addAll(calculate);
                }
            }

        } catch (Exception e) {
            log.error("动态筛选客户失败:", e);
        }

        if(CollectionUtil.isNotEmpty(weCustomerList)){//取出符合执行员工的所属客户
            weCustomerList=weCustomerList.stream().filter(weCustomer -> executeWeUserIds.contains(weCustomer.getAddUserId())).collect(Collectors.toList());
        }

        return weCustomerList;
    }


}

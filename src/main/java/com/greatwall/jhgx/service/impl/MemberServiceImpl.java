package com.greatwall.jhgx.service.impl;

import com.greatwall.component.ccyl.common.service.impl.SuperServiceImpl;
import com.greatwall.jhgx.domain.Member;
import com.greatwall.jhgx.mapper.MemberMapper;
import com.greatwall.jhgx.service.MemberService;
import org.springframework.stereotype.Service;

@Service
public class MemberServiceImpl extends SuperServiceImpl<MemberMapper, Member> implements MemberService {

    @Override
    public Member selectMinDayOrderAmtMember(String today) {
        return baseMapper.selectMinDayOrderAmtMember(today);
    }
}

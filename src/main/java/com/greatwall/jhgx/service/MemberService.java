package com.greatwall.jhgx.service;

import com.greatwall.component.ccyl.common.service.ISuperService;
import com.greatwall.jhgx.domain.Member;

public interface MemberService extends ISuperService<Member> {

    Member selectMinDayOrderAmtMember(String today);
}

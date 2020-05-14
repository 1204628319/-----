package com.greatwall.jhgx.mapper;

import com.greatwall.jhgx.domain.Member;

public interface MemberMapper extends SuperMapper<Member> {
    Member selectMinDayOrderAmtMember(String today);
}

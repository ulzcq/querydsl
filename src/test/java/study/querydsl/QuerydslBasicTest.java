package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @PersistenceContext
    EntityManager em;

    JPAQueryFactory queryFactory; //동시성 문제 없게 설계되어있음!

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        //member1을 찾아라.
        String qlString =
                "select m from Member m " +
                "where m.username = :username"; //오타 발생 시 Runtime 오류 발생

        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * 기본 Q-Type 활용
     * - 같은 테이블을 조인해야 하는 경우가 아니면 기본 인스턴스를 사용하자
     * - Querydsl은 컴파일 시점에 오류를 잡아줌
     */
    @Test
    public void startQuerydsl() {
        //member1을 찾아라.

        //1. 별칭 직접 지정
//        QMember m = new QMember("m");

        //*권장하는 방법
        //2. 기본 인스턴스를 static import와 함께 사용 (QMember.member)
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))//파라미터 바인딩 자동 처리
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /** 기본 검색 쿼리
     * - 검색 조건은 .and() , . or() 를 메서드 체인으로 연결할 수 있다.
     * > 참고: select , from 을 selectFrom 으로 합칠 수 있음
     */
    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * AND 조건을 파라미터로 처리
     * - where() 에 파라미터로 검색조건을 추가하면 AND 조건이 추가됨
     * - 이 경우 null 값은 무시 -> 메서드 추출을 활용해서 동적 쿼리를 깔끔하게 만들 수 있음(뒤에서 설명)
     */
    @Test
    public void searchAndParam() {
        List<Member> result1 = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10)
                )
                .fetch();

        assertThat(result1.size()).isEqualTo(1);
    }

    /** 결과 조회 */
    @Test
    public void resultFetch(){
        //List. 데이터 없으면 빈 리스트 반환(null 체크 필요X)
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        //단 건. 결과가 없으면 null, 둘 이상이면 NonUniqueResultException
        Member findMember1 = queryFactory
                .selectFrom(member)
                .fetchOne();

        //처음 한 건 조회
        Member findMember2 = queryFactory
                .selectFrom(member)
                .fetchFirst(); //limit(1).fetchOne()과 같다

        //fetchResults, fetchCount deprecated
        //이유: group by having 절을 사용하는 등의 복잡한 쿼리에서는 잘 작동하지 않음
        //컨텐츠를 가져오는 쿼리, total count 쿼리 따로 작성할 것
        //fetchCount 대신 fetch().size()를 사용할 것
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     * - nullsLast() , nullsFirst() : null 데이터 순서 부여
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        //나이는 같으니까 이름 오름차순으로 member5, 6 순서로 정렬됨
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    /**
     * 페이징
     * - 조회 건수 제한
     * - 전체 조회 수가 필요하면 따로 count 쿼리 작성
     */
    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //0부터 시작(zero index)
                .limit(2) //최대 2건 조회
                .fetch();

        assertThat(result.size()).isEqualTo(2);

        //total count 쿼리
        int totalSize = queryFactory
                .selectFrom(member)
                .fetch().size();

        assertThat(totalSize).isEqualTo(4);
    }


}
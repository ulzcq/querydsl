package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.group.GroupBy;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

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

    /**
     * 집합 함수
     * - JPQL이 제공하는 모든 집합 함수를 제공한다.
     * - tuple(Querydsl 제공)은 프로젝션과 결과반환에서 설명한다.
     *  - 여러 개의 타입이 있을 때 막 꺼내올 수 있음
     *  - 사실 실무에서 많이 쓰진 않고 DTO로 직접 보고 하는 방법을 씀
     */
    @Test
    public void aggregation() throws Exception {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),   //회원수
                        member.age.sum(), //나이 합
                        member.age.avg(), //평균 나이
                        member.age.max(), //최대 나이
                        member.age.min()  //최소 나이
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * GroupBy 사용
     * - 팀의 이름과 각 팀의 평균 연령을 구해라.
     * - groupBy , 그룹화된 결과를 제한하려면 having
     *   - 예) .having(item.price.gt(1000))
     */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); //(10 + 20) / 2

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); //(30 + 40) / 2
    }

    /**
     * 조인
     * - join(조인 대상, 별칭으로 사용할 Q타입)
     * - 예)팀 A에 소속된 모든 회원
     *
     * - join() , innerJoin() : 내부 조인(inner join)
     * - leftJoin() : left 외부 조인(left outer join)
     * - rightJoin() : rigth 외부 조인(rigth outer join)
     *
     * - JPQL의 on 과 성능 최적화를 위한 fetch 조인 제공 다음 on 절에서 설명
     */
    @Test
    public void join() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인(연관관계가 없는 필드로 조인)
     * - 예) 회원의 이름이 팀 이름과 같은 회원 조회
     *
     * - from 절에 여러 엔티티를 선택해서 세타 조인
     * - 외부(outer) 조인 불가능. But 다음에 설명할 조인 on을 사용하면 외부 조인 가능
     */
    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team) // 둘 모두 합친 후 where절에서 필터링(DB가 최적화함)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /** 조인 - on절
     * 1. 조인 대상 필터링
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     *
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.TEAM_ID=t.id and t.name='teamA'
     *
     * > 참고: on 절을 활용해 조인 대상을 필터링 할 때, 외부조인이 아니라 내부조인(inner join)을 사용하면,
     * where 절에서 필터링 하는 것과 기능이 동일하다. 따라서 on 절을 활용한 조인 대상 필터링을 사용할 때,
     * 내부조인 이면 익숙한 where 절로 해결하고, 정말 외부조인이 필요한 경우에만 이 기능을 사용하자.
     */
    @Test
    public void join_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team) //select가 여러가지 타입이니까 반환타입이 Tuple
                .from(member)

                //결과 동일, 정말 외부조인(매칭 안되면 null)이 필요한 경우에만 이 기능을 사용하자
//                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .join(member.team, team) // 내부 조인(매칭 되는 데이터만 가져옴)
                .where(team.name.eq("teamA"))

                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 2. 연관관계 없는 엔티티 외부 조인
     * 예) 회원의 이름과 팀의 이름이 같은 대상 외부 조인
     *
     * JPQL: SELECT m, t FROM Member m LEFT JOIN Team t on m.username = t.name
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.username = t.name
     *
     * 하이버네이트 5.1부터 on 을 사용해서 서로 관계가 없는 필드로 외부 조인하는 기능이 추가되었다.
     * 물론 내부 조인도 가능하다.
     *
     * > 주의! 문법을 잘 봐야 한다. leftJoin() 부분에 일반 조인과 다르게 엔티티 하나만 들어간다.
     * - 일반조인: leftJoin(member.team, team)
     * - on조인: from(member).leftJoin(team).on(xxx)
     */
    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) //id 없이 엔티티 이름으로만 조인
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("t=" + tuple);
        }
    }

    /**
     * 페치 조인 미적용
     * - 지연로딩으로 Member, Team SQL 쿼리 각각 실행
     */
    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() throws Exception {
        em.flush();
        em.clear();
        //페치 조인 테스트 할 때는 영속성 컨텍스트를 날리지 않으면 결과를 보기 어려움

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam()); //초기화 된 엔티티인지 확인
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    /**
     * 페치 조인 적용
     * - 즉시로딩으로 Member, Team SQL 쿼리 조인으로 한번에 조회
     * - join(), leftJoin() 등 조인 기능 뒤에 fetchJoin() 이라고 추가
     * - 페치 조인에 대한 자세한 내용은 JPA 기본편이나, 활용 2편을 참고
     */
    @Test
    public void fetchJoinUse() throws Exception {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin() //페치 조인
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 서브 쿼리 eq 사용
     * 예) 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() throws Exception {
        QMember memberSub = new QMember("memberSub"); //엘리어스가 중복되면 안되는 경우 따로 선언

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 서브 쿼리 goe 사용
     * 예) 나이가 평균 나이 이상인 회원
     */
    @Test
    public void subQueryGoe() throws Exception {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30,40);
    }

    /**
     * 서브쿼리 여러 건 처리, in 사용
     */
    @Test
    public void subQueryIn() throws Exception {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    /**
     * select 절에 subquery
     */
    @Test
    public void selectServeQuery() throws Exception{
        QMember memberSub = new QMember("memberSub");

        List<Tuple> fetch = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ).from(member)
                .fetch();

        for (Tuple tuple : fetch) {
            System.out.println("username = " + tuple.get(member.username));
            System.out.println("age = " + tuple.get(JPAExpressions.select(memberSub.age.avg())
                            .from(memberSub)));
        }
    }

    /** Case 문
     * - select, 조건절(where), order by에서 사용 가능
     * - 지금 같은 예제는 가급적이면 최소한의 필터링만 DB에서 하고, 프론트에서 수행해야함
     *
     * 단순한 조건
     */
    @Test
    public void basicCase() throws Exception{
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    //복잡한 조건
    @Test
    public void complexCase() throws Exception{
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * orderBy에서 Case 문 함께 사용하기
     * - Querydsl은 자바 코드로 작성하기 때문에 rankPath 처럼 복잡한 조건을 변수로 선언해서
     *   select 절, orderBy 절에서 함께 사용할 수 있다.
     *
     * 예를 들어서 다음과 같은 임의의 순서로 회원을 출력하고 싶다면?
     * 1. 0 ~ 30살이 아닌 회원을 가장 먼저 출력
     * 2. 0 ~ 20살 회원 출력
     * 3. 21 ~ 30살 회원 출력
     */
    @Test
    public void orderByWithCase() throws Exception{
        NumberExpression<Integer> rankPath = new CaseBuilder()
                .when(member.age.between(0, 20)).then(2)
                .when(member.age.between(21, 30)).then(1)
                .otherwise(3);

        List<Tuple> result = queryFactory
                .select(member.username, member.age, rankPath)
                .from(member)
                .orderBy(rankPath.desc())
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            Integer rank = tuple.get(rankPath);
            System.out.println("username = " + username + " age = " + age + " rank = " + rank);
        }
    }

    /** 상수, 문자 더하기
     * - 상수가 필요하면 Expressions.constant(xxx) 사용
     * - 한번씩 필요할 때가 있음
     *
     * > 참고: 아래와 같이 최적화가 가능하면 SQL에 constant 값을 넘기지 않는다.
     * 상수를 더하는 것 처럼 최적화가 어려우면 SQL에 constant 값을 넘긴다.
     */
    @Test
    public void constant() throws Exception{
        Tuple result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetchFirst();

        System.out.println("result = " + result);
    }

    /**
     * 문자 더하기 concat
     *
     * > 참고: member.age.stringValue() 부분이 중요한데, 문자가 아닌 다른 타입들은 stringValue() 로
     * 문자로 변환할 수 있다. 이 방법은 ENUM을 처리할 때도 자주 사용한다.
     */
    @Test
    public void concat() throws Exception{

        //{username}_{age}
        String result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        System.out.println("result = " + result);
    }

    /**
     * 프로젝션 결과 반환 - 기본
     * - 프로젝션 대상이 하나면 타입을 명확하게 지정할 수 있음
     */
    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * 프로젝션 결과 반환 - 튜플 조회
     * - 프로젝션 대상이 둘 이상이면 튜플이나 DTO로 조회
     * - 튜플은 Querydsl이 제공하며 여러 타입을 저장할 때 사용
     *   - 리파지토리 계층 까지만 쓰고 컨트롤러나 서비스 계층이 모르게 해야함(DTO로 변환)
     */
    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username=" + username);
            System.out.println("age=" + age);
        }
    }

    /**
     * 프로젝션 결과 반환 - DTO 조회
     * - 프로젝션 대상이 둘 이상이면 튜플이나 DTO로 조회
     * - 쿼리 날릴 때 최적화해서 필요한 것만 가져오고 싶을 때
     *
     * - 순수 JPA에서 DTO를 조회할 때는 new 명령어를 사용해야함
     * - DTO의 package이름을 다 적어줘야해서 지저분함
     * - 생성자 방식만 지원함
     */
    @Test
    public void findDtoByJPQL(){

        List<MemberDto> result = em.createQuery(
                "select new study.querydsl.dto.MemberDto(m.username, m.age) " +
                        "from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 프로젝션 결과 반환 - DTO 조회
     *
     * - Querydsl 빈 생성(Bean population)
     * - 결과를 DTO 반환할 때 사용, 다음 3가지 방법 지원
     * - 단점: Runtime error 발생 가능성
     * 1. 프로퍼티 접근
     */
    @Test
    public void findDtoBySetter (){
        //프로퍼티 접근 - Setter
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class, //dto get/setter, default 생성자 필요
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** 2. 필드 직접 접근 */
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class, //private 이어도 상관없이 필드에 꽂힘
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** 2. 필드 직접 접근 - 별칭 부여 */
    @Test
    public void findUserDtoByField() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> fetch = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"), //필드명.as("dto 별칭")
                        ExpressionUtils.as(
                                JPAExpressions
                                        .select(memberSub.age.max()) //억지 쿼리긴 한디 max 나이로 다 찍는거
                                        .from(memberSub), "age") //서브 쿼리의 결과가 age에 매칭되서 들어감
                        )
                ).from(member)
                .fetch();

        for (UserDto userdto : fetch) {
            System.out.println("memberDto = " + userdto);
        }
    }

    /** 3. 생성자 사용 */
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class, //필드 타입이 딱 맞아야함
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** 프로젝션과 결과 반환 - @QueryProjection
     *
     * - 이 방법은 컴파일러로 타입을 체크할 수 있으므로 가장 안전한 방법이다.
     * 다만 DTO에 QueryDSL 어노테이션을 유지해야 하는 점(의존)과 DTO까지 Q파일을 생성해야 하는 단점이 있다.
     */
    @Test
    public void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** 동적 쿼리 - 1. BooleanBuilder 사용 */
    @Test
    public void 동적쿼리_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        //BooleanBuilder builder = new BooleanBuilder(member.username.equ(usernameCond); //초기 필수값 설정 예시
        BooleanBuilder builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /** 동적 쿼리 - 2. Where 다중 파라미터 사용
     * - where 조건에 null 값은 무시된다.
     * - 메서드를 다른 쿼리에서도 재활용 할 수 있다.
     * - 쿼리 자체의 가독성이 높아진다.
     *
     * 훨씬 깔끔해지고 장점이 많아서 실무에서 선호하는 방식이라고 하심!
     */
    @Test
    public void 동적쿼리_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond))
//                .where(allEq(usernameCond, ageCond)) //조합 가능 예시
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    //조합 가능
    //null 체크는 주의해서 처리해야함
    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    /** 수정 벌크 연산
     * - 쿼리 한번으로 대량 데이터 수정
     *
     * > 주의: JPQL 배치와 마찬가지로, 영속성 컨텍스트에 있는 엔티티를 무시하고 실행되기 때문에
     * 배치 쿼리를 실행하고 나면 영속성 컨텍스트를 초기화 하는 것이 안전하다.
     */
    @Test
    public void bulkUpdate() {

        //member 1 = 10 -> DB 비회원
        //member 2 = 20 -> DB 비회원
        //member 3 = 30 -> DB member3
        //member 4 = 40 -> DB member4
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();
    }

    @Test
    public void bulkAdd() {
        //기존 숫자에 1 더하기
        long count2 = queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) //뺴고 싶으면 add(-숫자), 곱하기는 multiply
                .execute();

        em.flush();
        em.clear();
    }

    /** 삭제 벌크 연산
     * > 주의: JPQL 배치와 마찬가지로, 영속성 컨텍스트에 있는 엔티티를 무시하고 실행되기 때문에
     * 배치 쿼리를 실행하고 나면 영속성 컨텍스트를 초기화 하는 것이 안전하다.
     */
    @Test
    public void bulkDelete() {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();

        em.flush();
        em.clear();
    }

    /**
     * SQL function 호출하기
     * - SQL function은 JPA와 같이 Dialect에 등록된 내용만 호출할 수 있다.
     */
    @Test
    public void sqlFunction() {
        //replace 함수 사용
        String result = queryFactory
                .select(Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
                        member.username, "member", "M")) //이름에서 member라는 단어를 M으로 변경
                .from(member)
                .fetchFirst();

        System.out.println("result = " + result);
    }

    /** lower 같은 ansi 표준 함수들은 querydsl이 상당부분 내장하고 있다. 따라서 다음과 같이 처리해도 결과는 같다. */
    @Test
    public void sqlFunction2() {
        //소문자로 변경해서 비교해라.
        String result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower())) //querydsl 함수 사용
                .fetchFirst();

        System.out.println("result = " + result);
    }

}
package com.atguigu.gulimall.member.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.config.filter.IFilterConfig;
import com.atguigu.common.utils.HttpUtils;
import com.atguigu.gulimall.member.dao.MemberLevelDao;
import com.atguigu.gulimall.member.entity.MemberLevelEntity;
import com.atguigu.gulimall.member.exception.PhoneExistException;
import com.atguigu.gulimall.member.exception.UsernameExistException;
import com.atguigu.gulimall.member.vo.MemberLoginVo;
import com.atguigu.gulimall.member.vo.MemberRegistVo;
import com.atguigu.gulimall.member.vo.SocialUser;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.member.dao.MemberDao;
import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.service.MemberService;


@Service("memberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {

    @Autowired
    MemberLevelDao memberLevelDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                new QueryWrapper<MemberEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public void regist(MemberRegistVo vo) {
        MemberDao memberDao = this.baseMapper;
        MemberEntity entity = new MemberEntity();
        MemberLevelEntity levelEntity = memberLevelDao.getDefaultLevel();//获取默认等级信息
        entity.setLevelId(levelEntity.getId());//设置默认等级
        //检查用户名邮箱和手机号是否唯一
        checkPhoneUnique(vo.getPhone());
        checkUsernameUnique(vo.getUserName());

        entity.setMobile(vo.getPhone()); //设置手机号
        entity.setUsername(vo.getUserName());//设置用户名
        entity.setNickname(vo.getUserName());
        //密码加密才能存储
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String encode = passwordEncoder.encode(vo.getPassword());//原密码加密
        entity.setPassword(encode);

        memberDao.insert(entity);
    }

    /**
     * 检查邮箱是否唯一
     *
     * @param phone
     * @throws PhoneExistException
     */
    @Override
    public void checkPhoneUnique(String phone) throws PhoneExistException {
        MemberDao memberDao = this.baseMapper;
        Integer mobile = memberDao.selectCount(new QueryWrapper<MemberEntity>().eq("mobile", phone));
        if (mobile > 0) { //说明数据库有这条记录,就来抛异常
            throw new PhoneExistException();
        }
    }

    /**
     * 检查用户名是否唯一
     *
     * @param username
     * @throws UsernameExistException
     */
    @Override
    public void checkUsernameUnique(String username) throws UsernameExistException {
        MemberDao memberDao = this.baseMapper;
        Integer count = memberDao.selectCount(new QueryWrapper<MemberEntity>().eq("username", username));
        if (count > 0) { //说明数据库有这条记录,就来抛异常
            throw new UsernameExistException();
        }
    }

    /**
     * 登录逻辑
     *
     * @param vo
     * @return
     */
    @Override
    public MemberEntity login(MemberLoginVo vo) {
        String loginacct = vo.getLoginacct();
        String password = vo.getPassword();

        //1.去数据库查询数据
        MemberDao memberDao = this.baseMapper;
        MemberEntity entity = memberDao.selectOne(new QueryWrapper<MemberEntity>().eq("username", loginacct).or().eq("mobile", loginacct));
        if (entity == null) {  //数据库没有这个账号，登录失败
            return null;
        } else {
            //获取数据库的密码字段
            String passwordDb = entity.getPassword();
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            boolean matches = passwordEncoder.matches(password, passwordDb);//密码比较和匹配，方法的第一个参数是要求传明文的密码，第二个参数是传加密后的密码
            if (matches) {//如果匹配成功，就说明登录成功了
                return entity;//把当前的用户返回
            } else { //如果匹配不成功，就说明登录失败了
                return null;
            }
        }
    }


    /**
     * 使用社交账号进行登录
     * 没登陆过就进行注册，注册的目的就是为了保存当前这个社交用户，在我们数据库对应的哪个用户id
     *
     * @param socialUser
     * @return
     */
    @Override
    public MemberEntity login(SocialUser socialUser) throws Exception {
        //登录和注册合并
        //首先判断这个用户到低以前登录过没
        String uid = socialUser.getUid();//社交账号当前登录这个网站的微博id
        //1.根据uid来判断当前社交用户是否已经登录过系统
        MemberDao memberDao = this.baseMapper;
        MemberEntity memberEntity = memberDao.selectOne(new QueryWrapper<MemberEntity>().eq("social_uid", uid));
        if (memberEntity != null) { //说明这个用户已经注册过的
            //一旦如果是已经注册过的，那么就更新令牌以及更新时间
            MemberEntity update = new MemberEntity();
            update.setId(memberEntity.getId());
            update.setAccessToken(socialUser.getAccess_token());
            update.setExpiresIn(socialUser.getExpires_in());
            memberDao.updateById(update);
            memberEntity.setAccessToken(socialUser.getAccess_token());
            memberEntity.setExpiresIn(socialUser.getExpires_in());
            return memberEntity;
        } else { //说明这个用户还没有注册过的
            //没有查到当前社交的用户对应的记录，就需要注册
            MemberEntity regist = new MemberEntity();
            //即使远程查询出现有问题了也没关系，也不用管
            try {
                //查询当前社交用户的社交账号信息（昵称，性别等信息）
                Map<String, String> query = new HashMap<>();//用于封装查询参数的
                query.put("access_token", socialUser.getAccess_token());
                query.put("uid", socialUser.getUid());
                //查出用户的详细信息
                HttpResponse response = HttpUtils.doGet("https://api.weibo.com", "/2/users/show.json", "get", new HashMap<String, String>(), query);
                if (response.getStatusLine().getStatusCode() == 200) { //查询成功，有用户的详细数据
                    String json = EntityUtils.toString(response.getEntity());
                    JSONObject jsonObject = JSON.parseObject(json);
                    String name = jsonObject.getString("name");//获取当前微博登录成功后的名称
                    String gender = jsonObject.getString("gender");//获取当前微博登录成功后的性别
                    //进行注册
                    regist.setNickname(name); //设置默认的昵称
                    regist.setGender("m".equals(gender) ? 1 : 0);
                }
            } catch (Exception e) {

            }
            //以后只要社交用户是第一次登陆，那么数据库里面就会有一条记录，那么相当于它就是注册进来了
            regist.setSocialUid(socialUser.getUid());//设置当前微博登录成功后的Uid
            regist.setAccessToken(socialUser.getAccess_token());//设置当前登录后的令牌
            regist.setExpiresIn(socialUser.getExpires_in());//设置当前登录后的令牌过期时间
            //插入数据
            memberDao.insert(regist);
            //插入数据成功以后，说明这个用户也就登陆成功了
            return regist;
        }

    }
}
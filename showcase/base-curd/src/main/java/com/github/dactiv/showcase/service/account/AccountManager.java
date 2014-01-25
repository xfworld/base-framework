package com.github.dactiv.showcase.service.account;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.dactiv.common.utils.CollectionUtils;
import com.github.dactiv.orm.core.Page;
import com.github.dactiv.orm.core.PageRequest;
import com.github.dactiv.orm.core.PropertyFilter;
import com.github.dactiv.orm.core.PropertyFilters;
import com.github.dactiv.showcase.common.SystemVariableUtils;
import com.github.dactiv.showcase.common.enumeration.entity.GroupType;
import com.github.dactiv.showcase.common.enumeration.entity.ResourceType;
import com.github.dactiv.showcase.dao.account.GroupDao;
import com.github.dactiv.showcase.dao.account.ResourceDao;
import com.github.dactiv.showcase.dao.account.UserDao;
import com.github.dactiv.showcase.entity.account.Group;
import com.github.dactiv.showcase.entity.account.Resource;
import com.github.dactiv.showcase.entity.account.User;
import com.github.dactiv.showcase.service.ServiceException;
import com.google.common.collect.Lists;

/**
 * 账户管理业务逻辑
 * 
 * @author maurice
 *
 */
@Service
@Transactional
public class AccountManager {
	
	//用户数据访问
	@Autowired
	private UserDao userDao;
	
	//资源数据访问
	@Autowired
	private ResourceDao resourceDao;
	
	//组数据访问
	@Autowired
	private GroupDao groupDao;
	
	//------------------------------用户管理-----------------------------------//
	
	/**
	 * 更新当前用户密码
	 * 
	 * @param oldPassword 旧密码
	 * @param newPassword 新密码
	 * 
	 */
	//当修改成功后将shiro的认证缓存也更新，包正下次登录也不需要在次查询数据库
	@CacheEvict(value="shiroAuthenticationCache",
			  	key="T(com.github.dactiv.showcase.common.SystemVariableUtils)." +
					"getSessionVariable()." +
					"getUser()." +
					"getUsername()")
	public void updateUserPassword(String oldPassword, String newPassword) {
		User user = SystemVariableUtils.getSessionVariable().getUser();
		
		oldPassword = new SimpleHash("MD5", oldPassword.toCharArray()).toString();
		
		if (!user.getPassword().equals(oldPassword)) {
			throw new ServiceException("旧密码不正确.");
		}
		
		String temp = new SimpleHash("MD5",newPassword).toHex();
		userDao.updatePassword(user.getId(),temp);
		user.setPassword(temp); 
	}
	
	
	/**
	 * 通过id获取用户实体
	 * @param id 用户id
	 */
	public User getUser(String id) {
		return userDao.load(id);
	}
	
	/**
	 * 通过属性过滤器查询用户分页
	 * 
	 * @param request 分页参数
	 * @param filters 属性过滤器集合
	 * 
	 * @return {@link Page}
	 */
	public Page<User> searchUserPage(PageRequest request,List<PropertyFilter> filters) {
		
		return userDao.findPage(request, filters);
		
	}
	
	/**
	 * 新增用户
	 * 
	 * @param entity 用户实体
	 */
	public void insertUser(User entity) {
		if (!isUsernameUnique(entity.getUsername())) {
			throw new ServiceException("用户名已存在");
		}
		
		String password = new SimpleHash("MD5", entity.getPassword()).toHex();
		
		entity.setPassword(password);
		userDao.insert(entity);
	}
	
	/**
	 * 更新用户
	 * 
	 * @param entity 用户实体
	 */
	//当更新后将shiro的认证缓存也更新，保证shiro和当前的用户一致
	@CacheEvict(value="shiroAuthenticationCache",key="#entity.getUsername()")
	public void updateUser(User entity) {
		userDao.update(entity);
	}
	
	/**
	 * 是否唯一的用户名 如果是返回true,否则返回false
	 * 
	 * @param username 用户名
	 * 
	 * @return boolean
	 */
	public boolean isUsernameUnique(String username) {
		return getUserByUsername(username) == null;
	}
	
	/**
	 * 删除用户
	 * 
	 * @param ids 用户id集合
	 */
	public void deleteUsers(List<String> ids) {
		userDao.deleteAll(ids);
	}

	/**
	 * 通过用户名获取用户实体
	 * 
	 * @param username 用户实体
	 * 
	 * @return {@link User}
	 */
	public User getUserByUsername(String username) {
		return userDao.findUniqueByProperty("username", username);
	}
	
	//------------------------------资源管理-----------------------------------//
	
	/**
	 * 通过id获取资源实体
	 * 
	 * @param id 资源id
	 * 
	 * @return {@link Resource}
	 */
	public Resource getResource(String id) {
		return resourceDao.load(id);
	}
	
	/**
	 * 通过id集合获取资源资源
	 * 
	 * @param ids 资源集合
	 * 
	 * @return List
	 */
	public List<Resource> getResources(List<String> ids) {
		return resourceDao.get(ids);
	}
	
	/**
	 * 获取最顶级(父类)的资源集合
	 * 
	 * @return List
	 */
	public List<Resource> getParentResources() {
		List<PropertyFilter> filters = Lists.newArrayList(
			PropertyFilters.build("EQS_parent.id","null")
		);
		return resourceDao.findByPropertyFilter(filters, Order.asc("sort"));
	}
	
	/**
	 * 保存资源实体
	 * 
	 * @param entity 资源实体
	 */
	@CacheEvict(value="shiroAuthorizationCache",allEntries=true)
	public void saveResource(Resource entity) {
		
		//如果sort等于null值，设置一个最新的值给entity
		if(entity.getSort() == null) {
			entity.setSort(resourceDao.entityCount() + 1);
		}
		
		//如果他父类不为null，将父类的leaf设置成true，表示父类下存在子节点
		if (entity.getParent() != null) {
			entity.getParent().setLeaf(true);
		}
		
		resourceDao.save(entity);
		resourceDao.refreshAllLeaf();
	}
	
	/**
	 * 通过资源实体集合删除资源
	 * 
	 * @param resources 资源实体集合 
	 */
	@CacheEvict(value="shiroAuthorizationCache",allEntries=true)
	public void deleteResources(List<Resource> resources) {
		
		if (CollectionUtils.isEmpty(resources)) {
			return ;
		}
		
		for (Resource entity : resources) {
			resourceDao.delete(entity);
		}
		resourceDao.refreshAllLeaf();
	}
	
	/**
	 * 获取所有资源
	 * 
	 * @param ignoreIdValue 要忽略的id属性值
	 * 
	 * @return List
	 */
	public List<Resource> getResources(String... ignoreIdValue) {
		List<PropertyFilter> filters = new ArrayList<PropertyFilter>();
		
		if (ArrayUtils.isNotEmpty(ignoreIdValue)) {
			filters.add(PropertyFilters.build("NES_id", StringUtils.join(ignoreIdValue,",")));
		}
		
		return resourceDao.findByPropertyFilter(filters, Order.asc("sort"));
	}
	
	/**
	 * 获取资源实体的总记录数
	 * 
	 * @return long
	 */
	public long getResourceCount() {
		return resourceDao.entityCount();
	}
	
	/**
	 * 通过用户id获取该用户下的所有资源
	 * 
	 * @param userId 用户id
	 * 
	 * @return List
	 */
	public List<Resource> getUserResources(String userId) {
		return resourceDao.getUserResources(userId);
	}
	
	/**
	 * 并合子类资源到父类中，返回一个新的资源集合
	 * 
	 * @param list 资源集合
	 * @param resourceType 不需要并合的资源类型
	 */
	public List<Resource> mergeResourcesToParent(List<Resource> list,ResourceType ignoreType) {
		return resourceDao.mergeToParent(list,ignoreType);
	}
	
	//------------------------------组管理-----------------------------------//
	
	/**
	 * 通过id获取组实体
	 * 
	 * @param id 组id
	 * 
	 * @return {@link Group}
	 */
	public Group getGroup(String id) {
		return groupDao.load(id);
	}
	
	/**
	 * 通过组id，获取组集合
	 * 
	 * @param ids id集合
	 * 
	 * @return List
	 */
	public List<Group> getGroups(List<String> ids) {
		return groupDao.get(ids);
	}
	
	/**
	 * 保存组实体
	 * 
	 * @param entity 组实体
	 */
	@CacheEvict(value="shiroAuthorizationCache",allEntries=true)
	public void saveGroup(Group entity) {
		//如果他父类不为null，将父类的leaf设置成true，表示父类下存在子节点
		if (entity.getParent() != null) {
			entity.getParent().setLeaf(true);
		}
		groupDao.save(entity);
		groupDao.refreshAllLeaf();
	}
	
	/**
	 * 删除组实体
	 * 
	 * @param ids 组id
	 */
	@CacheEvict(value="shiroAuthorizationCache",allEntries=true)
	public void deleteGroups(List<String> ids) {
		groupDao.deleteAll(ids);
		groupDao.refreshAllLeaf();
	}
	
	/**
	 * 根据组类型获取所有组信息
	 * 
	 * @param groupType 组类型
	 * @param ignoreIdValue 要忽略的id属性值
	 * 
	 * @return List
	 */
	public List<Group> getGroups(GroupType groupType,String... ignoreIdValue) {
		
		List<PropertyFilter> filters = new ArrayList<PropertyFilter>();
		
		if (ArrayUtils.isNotEmpty(ignoreIdValue)) {
			filters.add(PropertyFilters.build("NES_id", StringUtils.join(ignoreIdValue,",")));
		}
		
		filters.add(PropertyFilters.build("EQS_type", groupType.getValue()));
		
		return groupDao.findByPropertyFilter(filters);
	}

	/**
	 * 获取最顶级(父类)的组集合
	 * 
	 * @param type 组类型
	 * 
	 * @return List
	 */
	public List<Group> getParentGroups(GroupType type) {
		List<PropertyFilter> filters = Lists.newArrayList(
			PropertyFilters.build("EQS_parent.id","null"),
			PropertyFilters.build("EQS_type", type.getValue())
		);
		return groupDao.findByPropertyFilter(filters, Order.asc("id"));
	}

}

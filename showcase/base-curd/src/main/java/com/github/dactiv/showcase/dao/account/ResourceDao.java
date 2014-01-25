package com.github.dactiv.showcase.dao.account;

import java.util.ArrayList;
import java.util.List;

import com.github.dactiv.orm.core.hibernate.support.HibernateSupportDao;
import com.github.dactiv.showcase.common.enumeration.entity.ResourceType;
import com.github.dactiv.showcase.entity.account.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

/**
 * 资源数据访问
 * 
 * @author maurice
 *
 */
@Repository
public class ResourceDao extends HibernateSupportDao<Resource, String>{

	/**
	 * 通过用户id获取用户所有资源
	 * 
	 * @param userId 用户id
	 * 
	 * @return List
	 */
	public List<Resource> getUserResources(String userId) {
		return distinct(Resource.UserResources, userId);
	}
	
	/**
	 * 刷新一次Resource的leaf字段，如果该leaf = 1 并且该资源没有子类，把该资源的leaf改成0
	 */
	public void refreshAllLeaf() {
		List<Resource> list = findByQuery(Resource.LeafTureNotAssociated);
		for (Resource entity : list) {
			entity.setLeaf(false);
			save(entity);
		}
		
	}
	
	/**
	 * 并合子类资源到父类中，返回一个新的资源集合
	 * 
	 * @param list 资源集合
	 * @param resourceType 不需要并合的资源类型
	 */
	public List<Resource> mergeToParent(List<Resource> list,ResourceType ignoreType) {
		//将当前session直接clear，避免自动更新问题。
		getSessionFactory().getCurrentSession().clear();
		
		List<Resource> result = new ArrayList<Resource>();
		
		for (Resource r : list) {
			if (r.getParent() == null && !StringUtils.equals(ignoreType.getValue(),r.getType())) {
				mergeToParent(list,r,ignoreType);
				result.add(r);
			}
		}
		
		return result;
	}
	
	/**
	 * 遍历list中的数据,如果数据的父类与parent相等，将数据加入到parent的children中
	 * 
	 * @param list 资源集合
	 * @param parent 父类对象
	 * @param ignoreType 不需要加入到parent的资源类型
	 */
	private void mergeToParent(List<Resource> list, Resource parent,ResourceType ignoreType) {
		if (!parent.getLeaf()) {
			return ;
		}
		
		parent.setChildren(new ArrayList<Resource>());
		parent.setLeaf(false);
		
		for (Resource r: list) {
			//这是一个递归过程，如果当前遍历的r资源的parentId等于parent父类对象的id，将会在次递归r对象。通过遍历list是否也存在r对象的子级。
			if (!StringUtils.equals(r.getType(), ignoreType.getValue()) && StringUtils.equals(r.getParentId(),parent.getId()) ) {
				r.setChildren(null);
				mergeToParent(list,r,ignoreType);
				parent.getChildren().add(r);
				parent.setLeaf(true);
			}
			
		}
	}
	
}

package com.mashang.dictconfig.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotBlank;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alicp.jetcache.anno.Cached;
import com.mashang.common.exception.BizError;
import com.mashang.common.exception.BizException;
import com.mashang.common.valid.ParamValid;
import com.mashang.common.vo.PageResult;
import com.mashang.dictconfig.domain.DictItem;
import com.mashang.dictconfig.domain.DictType;
import com.mashang.dictconfig.param.AddOrUpdateDictTypeParam;
import com.mashang.dictconfig.param.DictDataParam;
import com.mashang.dictconfig.param.DictTypeQueryCondParam;
import com.mashang.dictconfig.param.UpdateDictDataParam;
import com.mashang.dictconfig.repo.DictItemRepo;
import com.mashang.dictconfig.repo.DictTypeRepo;
import com.mashang.dictconfig.vo.DictItemVO;
import com.mashang.dictconfig.vo.DictTypeVO;

import cn.hutool.core.util.StrUtil;

@Service
public class DictService {

	@Autowired
	private DictItemRepo dictItemRepo;

	@Autowired
	private DictTypeRepo dictTypeRepo;

	@ParamValid
	@Transactional
	public void updateDictData(UpdateDictDataParam param) {
		Set<String> dictItemCodeSet = new HashSet<String>();
		for (DictDataParam dictDataParam : param.getDictDatas()) {
			if (!dictItemCodeSet.add(dictDataParam.getDictItemCode())) {
				throw new BizException(BizError.字典项code不能重复);
			}
		}

		DictType dictType = dictTypeRepo.getOne(param.getDictTypeId());
		dictItemRepo.deleteAll(dictType.getDictItems());

		double orderNo = 1;
		for (DictDataParam dictDataParam : param.getDictDatas()) {
			DictItem dictItem = dictDataParam.convertToPo();
			dictItem.setDictTypeId(dictType.getId());
			dictItem.setOrderNo(orderNo);
			dictItemRepo.save(dictItem);
			
			orderNo++;
		}
	}

	@ParamValid
	@Transactional
	public void delDictTypeById(@NotBlank String id) {
		DictType dictType = dictTypeRepo.getOne(id);
		dictItemRepo.deleteAll(dictType.getDictItems());
		dictTypeRepo.delete(dictType);
	}

	@ParamValid
	@Transactional(readOnly = true)
	public DictTypeVO findDictTypeById(@NotBlank String id) {
		return DictTypeVO.convertFor(dictTypeRepo.getOne(id));
	}

	@ParamValid
	@Transactional
	public void addOrUpdateDictType(AddOrUpdateDictTypeParam param) {
		// 新增
		if (StrUtil.isBlank(param.getId())) {
			DictType dictType = param.convertToPo();
			dictTypeRepo.save(dictType);
		}
		// 修改
		else {
			DictType dictType = dictTypeRepo.getOne(param.getId());
			BeanUtils.copyProperties(param, dictType);
			dictTypeRepo.save(dictType);
		}
	}

	@Transactional(readOnly = true)
	public PageResult<DictTypeVO> findDictTypeByPage(DictTypeQueryCondParam param) {
		Specification<DictType> spec = new Specification<DictType>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<DictType> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				if (StrUtil.isNotBlank(param.getDictTypeCode())) {
					predicates.add(builder.like(root.get("dictTypeCode"), "%" + param.getDictTypeCode() + "%"));
				}
				if (StrUtil.isNotBlank(param.getDictTypeName())) {
					predicates.add(builder.like(root.get("dictTypeName"), "%" + param.getDictTypeName() + "%"));
				}
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<DictType> result = dictTypeRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("dictTypeCode"))));
		PageResult<DictTypeVO> pageResult = new PageResult<>(DictTypeVO.convertFor(result.getContent()),
				param.getPageNum(), param.getPageSize(), result.getTotalElements());
		return pageResult;
	}

	@Cached(name = "dictItem_", key = "args[0] + '_' +  args[1]", expire = 3600)
	@Transactional(readOnly = true)
	public DictItemVO findDictItemByDictTypeCodeAndDictItemCode(String dictTypeCode, String dictItemCode) {
		return DictItemVO
				.convertFor(dictItemRepo.findByDictTypeDictTypeCodeAndDictItemCode(dictTypeCode, dictItemCode));
	}

	@Cached(name = "dictItems_", key = "args[0]", expire = 3600)
	@Transactional(readOnly = true)
	public List<DictItemVO> findDictItemByDictTypeCode(String dictTypeCode) {
		return DictItemVO.convertFor(dictItemRepo.findByDictTypeDictTypeCodeOrderByOrderNo(dictTypeCode));
	}

	@Transactional(readOnly = true)
	public List<DictItemVO> findDictItemByDictTypeId(String dictTypeId) {
		return DictItemVO.convertFor(dictItemRepo.findByDictTypeIdOrderByOrderNo(dictTypeId));
	}

}

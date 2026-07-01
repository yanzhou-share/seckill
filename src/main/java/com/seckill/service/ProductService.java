package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.entity.Product;
import com.seckill.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    public List<Product> listAll() {
        return productMapper.selectList(null);
    }

    public Product getById(Long id) {
        return productMapper.selectById(id);
    }

    public Product create(Product product) {
        productMapper.insert(product);
        return product;
    }

    public void update(Product product) {
        productMapper.updateById(product);
    }

    public void delete(Long id) {
        productMapper.deleteById(id);
    }
}

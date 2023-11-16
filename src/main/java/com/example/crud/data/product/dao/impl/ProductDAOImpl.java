package com.example.crud.data.product.dao.impl;

import com.example.crud.data.product.dao.ProductDAO;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProductDAOImpl implements ProductDAO {

    private final ProductRepository productRepository;

    @Autowired
    public ProductDAOImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;}

    @Override
    public Product insertProduct(Product product) {
        Product saveProduct = productRepository.save(product);
        return saveProduct;
    }

    @Override
    public Product selectProduct(Long number) {
        Product selectProduct = productRepository.getById(number);
        return selectProduct;
    }

    @Override
    public Product updateProductName(Long number, String name) throws Exception {
        Optional<Product> selectProduct = productRepository.findById(number);

        Product updateProduct;
        if (selectProduct.isPresent()) {
            Product product = selectProduct.get();

            product.setName(name);

            updateProduct = productRepository.save(product);
        } else {
            throw new Exception();
        }
        return updateProduct;
    }

    @Override
    public void deleteProduct(Long number) throws Exception {
        Optional<Product> seletedProduct = productRepository.findById(number);

        if (seletedProduct.isPresent()) {
            Product product = seletedProduct.get();

            productRepository.delete(product);
        }
        else {
            throw new Exception();
        }
    }

}

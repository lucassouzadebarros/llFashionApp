package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.NuvemshopApiClient;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopProductResponse;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopVariantResponse;
import br.com.llfashion.whatsappcheckout.dto.response.ProductSyncResponse;
import br.com.llfashion.whatsappcheckout.entity.NuvemshopInstallation;
import br.com.llfashion.whatsappcheckout.mapper.NuvemshopProductMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductSyncService {

    private static final int PER_PAGE = 200;

    private final NuvemshopInstallationService installationService;
    private final NuvemshopApiClient apiClient;
    private final ProductMappingService productMappingService;
    private final NuvemshopProductMapper productMapper;

    public ProductSyncService(
            NuvemshopInstallationService installationService,
            NuvemshopApiClient apiClient,
            ProductMappingService productMappingService,
            NuvemshopProductMapper productMapper
    ) {
        this.installationService = installationService;
        this.apiClient = apiClient;
        this.productMappingService = productMappingService;
        this.productMapper = productMapper;
    }

    @Transactional
    public ProductSyncResponse syncProducts() {
        NuvemshopInstallation installation = installationService.getActiveInstallation();

        int page = 1;
        int totalProductsRead = 0;
        int totalVariantsSynced = 0;

        while (true) {
            List<NuvemshopProductResponse> products = apiClient.buscarProdutos(
                    installation.getStoreId(),
                    installation.getAccessToken(),
                    page,
                    PER_PAGE
            );

            if (products.isEmpty()) {
                break;
            }

            totalProductsRead += products.size();
            for (NuvemshopProductResponse product : products) {
                totalVariantsSynced += syncProductVariants(product);
            }

            if (products.size() < PER_PAGE) {
                break;
            }

            page++;
        }

        return new ProductSyncResponse(
                totalProductsRead,
                totalVariantsSynced,
                "Sincronização concluída com sucesso"
        );
    }

    @Transactional
    public int syncProductById(Long productId) {
        if (productId == null) {
            return 0;
        }
        NuvemshopInstallation installation = installationService.getActiveInstallation();
        NuvemshopProductResponse product = apiClient.buscarProduto(
                installation.getStoreId(),
                installation.getAccessToken(),
                productId
        );
        return syncProductVariants(product);
    }

    private int syncProductVariants(NuvemshopProductResponse product) {
        if (product == null || product.id() == null || product.variants() == null) {
            return 0;
        }

        String productName = productMapper.resolveProductName(product);
        int total = 0;

        for (NuvemshopVariantResponse variant : product.variants()) {
            if (variant == null || variant.id() == null) {
                continue;
            }

            productMappingService.saveOrUpdateFromNuvemshop(
                    product.id(),
                    variant.id(),
                    variant.sku(),
                    productName,
                    productMapper.resolveVariantName(variant),
                    productMapper.resolveVariantImageUrl(product, variant),
                    variant.price(),
                    variant.stock()
            );
            total++;
        }

        return total;
    }
}

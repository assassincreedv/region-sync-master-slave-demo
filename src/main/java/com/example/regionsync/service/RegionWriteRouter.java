package com.example.regionsync.service;

import com.example.regionsync.config.RegionConfig;
import com.example.regionsync.model.DataCategory;
import com.example.regionsync.model.DataItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 写操作路由器 — 核心组件。
 *
 * <p>当用户在任意 Region 发起写操作时：
 * <ol>
 *   <li>如果本节点是该数据的 Master → 直接本地写入</li>
 *   <li>如果本节点不是该数据的 Master → 通过 REST 转发到 Master Region</li>
 * </ol>
 *
 * <p>这个方案本质上就是 "每条数据只有一个 Master"，从而彻底避免了冲突问题。
 *
 * <pre>
 * 场景: EU 用户想修改一个归属于 NA 的 Job
 * → 不是直接写本地 DB，而是通过 REST 调用 NA 的 API
 * → NA 写入后通过 SyncService 将变更推送给 EU 和 CN
 * </pre>
 */
@Component
public class RegionWriteRouter {

    private static final Logger log = LoggerFactory.getLogger(RegionWriteRouter.class);

    private final RegionConfig regionConfig;
    private final RestTemplate restTemplate;

    public RegionWriteRouter(RegionConfig regionConfig, RestTemplate restTemplate) {
        this.regionConfig = regionConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * 判断是否应该本地处理写操作。
     *
     * @param category  数据类别
     * @param ownerRegion 数据归属 Region（对于 REGIONAL 数据）
     * @return true=本地写, false=需要转发
     */
    public boolean shouldWriteLocally(DataCategory category, String ownerRegion) {
        return regionConfig.isOwnerOf(category, ownerRegion);
    }

    /**
     * 将写操作转发到数据归属的 Master Region。
     *
     * @param category    数据类别
     * @param ownerRegion 数据归属的 Region ID（对于 REGIONAL 类型）
     * @param requestBody 原始请求体
     * @return 远程 Master Region 返回的 DataItem
     */
    public DataItem forwardCreate(DataCategory category, String ownerRegion, Map<String, String> requestBody) {
        String targetRegion = resolveTargetRegion(category, ownerRegion);
        String targetUrl = regionConfig.getPeerUrl(targetRegion);

        if (targetUrl == null) {
            throw new IllegalStateException(
                    "Cannot forward write: no URL configured for region [" + targetRegion + "]");
        }

        String url = normalizeUrl(targetUrl) + "/api/data";
        log.info("Forwarding CREATE to master region [{}]: url={}, category={}", targetRegion, url, category);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<DataItem> response = restTemplate.postForEntity(url, entity, DataItem.class);
        log.info("Forward CREATE response from [{}]: status={}", targetRegion, response.getStatusCode());
        return response.getBody();
    }

    /**
     * 将更新操作转发到数据归属的 Master Region。
     */
    public DataItem forwardUpdate(DataCategory category, String ownerRegion,
                                  String key, Map<String, String> requestBody) {
        String targetRegion = resolveTargetRegion(category, ownerRegion);
        String targetUrl = regionConfig.getPeerUrl(targetRegion);

        if (targetUrl == null) {
            throw new IllegalStateException(
                    "Cannot forward write: no URL configured for region [" + targetRegion + "]");
        }

        String url = normalizeUrl(targetUrl) + "/api/data/" + key;
        log.info("Forwarding UPDATE to master region [{}]: url={}", targetRegion, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        restTemplate.put(url, entity);
        // PUT 不直接返回 body，再 GET 一下
        return restTemplate.getForObject(url, DataItem.class);
    }

    /**
     * 将删除操作转发到数据归属的 Master Region。
     */
    public void forwardDelete(DataCategory category, String ownerRegion, String key) {
        String targetRegion = resolveTargetRegion(category, ownerRegion);
        String targetUrl = regionConfig.getPeerUrl(targetRegion);

        if (targetUrl == null) {
            throw new IllegalStateException(
                    "Cannot forward delete: no URL configured for region [" + targetRegion + "]");
        }

        String url = normalizeUrl(targetUrl) + "/api/data/" + key;
        log.info("Forwarding DELETE to master region [{}]: url={}", targetRegion, url);
        restTemplate.delete(url);
    }

    /**
     * 解析目标 Region ID。
     * 对于全局固定 Master 的类型（SYSTEM_CONFIG, ROLE_PERMISSION），返回 categoryOwnerMap 中的值。
     * 对于 REGIONAL 类型，返回 ownerRegion。
     */
    private String resolveTargetRegion(DataCategory category, String ownerRegion) {
        String masterRegion = regionConfig.getMasterRegionFor(category);
        if ("REGIONAL".equalsIgnoreCase(masterRegion)) {
            return ownerRegion;
        }
        return masterRegion;
    }

    private static String normalizeUrl(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}

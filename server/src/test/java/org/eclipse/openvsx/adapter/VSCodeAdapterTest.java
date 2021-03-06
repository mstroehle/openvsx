/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.adapter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;

import javax.persistence.EntityManager;

import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.ExtensionSearch;
import org.eclipse.openvsx.search.SearchService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.util.Streamable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VSCodeAdapter.class)
@AutoConfigureWebClient
@MockBean({ ClientRegistrationRepository.class })
public class VSCodeAdapterTest {

    @MockBean
    RepositoryService repositories;

    @MockBean
    SearchService search;

    @MockBean
    EntityManager entityManager;

    @Autowired
    MockMvc mockMvc;

    @Test
    public void testSearch() throws Exception {
        mockSearch();
        mockMvc.perform(post("/vscode/gallery/extensionquery")
                .content(file("search-yaml-query.json"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(file("search-yaml-response.json")));
    }

    @Test
    public void testFindById() throws Exception {
        mockSearch();
        mockMvc.perform(post("/vscode/gallery/extensionquery")
                .content(file("findid-yaml-query.json"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(file("findid-yaml-response.json")));
    }

    @Test
    public void testFindByName() throws Exception {
        mockSearch();
        mockMvc.perform(post("/vscode/gallery/extensionquery")
                .content(file("findname-yaml-query.json"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(file("findname-yaml-response.json")));
    }

    @Test
    public void testAsset() throws Exception {
        mockAsset();
        mockMvc.perform(get("/vscode/asset/{namespace}/{extensionName}/{version}/{assetType}",
                    "redhat", "vscode-yaml", "0.5.2", "Microsoft.VisualStudio.Code.Manifest"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"foo\":\"bar\"}"));
    }

    @Test
    public void testAssetNotFound() throws Exception {
        mockAsset();
        mockMvc.perform(get("/vscode/asset/{namespace}/{extensionName}/{version}/{assetType}",
                    "redhat", "vscode-yaml", "0.5.2", "Microsoft.VisualStudio.Services.Content.Details"))
                .andExpect(status().isNotFound());
    }

    // ---------- UTILITY ----------//

    private void mockSearch() {
        var extVersion = mockExtension();
        var extension = extVersion.getExtension();
        extension.setId(1l);
        var entry1 = new ExtensionSearch();
        entry1.id = 1;
        var page = new PageImpl<>(Lists.newArrayList(entry1));
        Mockito.when(search.isEnabled())
                .thenReturn(true);
        Mockito.when(search.search("yaml", null, PageRequest.of(0, 50), "desc", "relevance"))
                .thenReturn(page);
        Mockito.when(entityManager.find(Extension.class, 1l))
                .thenReturn(extension);
    }

    private void mockAsset() {
        var extension = mockExtension();
        var manifest = new FileResource();
        manifest.setExtension(extension);
        manifest.setType(FileResource.MANIFEST);
        manifest.setContent("{\"foo\":\"bar\"}".getBytes());
        Mockito.when(repositories.findFile(extension, FileResource.MANIFEST))
                .thenReturn(manifest);
    }
    
    private ExtensionVersion mockExtension() {
        var namespace = new Namespace();
        namespace.setId(2);
        namespace.setName("redhat");
        var extension = new Extension();
        extension.setId(1);
        extension.setName("vscode-yaml");
        extension.setNamespace(namespace);
        extension.setDownloadCount(100);
        extension.setAverageRating(3.0);
        var extVersion = new ExtensionVersion();
        extension.setLatest(extVersion);
        extVersion.setExtension(extension);
        extVersion.setVersion("0.5.2");
        extVersion.setPreview(true);
        extVersion.setTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        extVersion.setDisplayName("YAML");
        extVersion.setDescription("YAML Language Support");
        extVersion.setReadmeFileName("README.md");
        extVersion.setLicenseFileName("LICENSE.txt");
        extVersion.setIconFileName("icon128.png");
        extVersion.setExtensionFileName("redhat.vscode-yaml-0.5.2.vsix");
        extVersion.setRepository("https://github.com/redhat-developer/vscode-yaml");
        extVersion.setEngines(Lists.newArrayList("vscode@^1.31.0"));
        extVersion.setDependencies(Lists.newArrayList());
        extVersion.setBundledExtensions(Lists.newArrayList());
        Mockito.when(repositories.findExtension("vscode-yaml", "redhat"))
                .thenReturn(extension);
        Mockito.when(repositories.findVersion("0.5.2", "vscode-yaml", "redhat"))
                .thenReturn(extVersion);
        Mockito.when(repositories.findVersions(extension))
                .thenReturn(Streamable.of(extVersion));
        Mockito.when(repositories.countMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                .thenReturn(0l);
        Mockito.when(repositories.countActiveReviews(extension))
                .thenReturn(10l);
        return extVersion;
    }

    private String file(String name) throws UnsupportedEncodingException, IOException {
        try (
            var stream = getClass().getResourceAsStream(name);
        ) {
            return CharStreams.toString(new InputStreamReader(stream, "UTF-8"));
        }
    }

}
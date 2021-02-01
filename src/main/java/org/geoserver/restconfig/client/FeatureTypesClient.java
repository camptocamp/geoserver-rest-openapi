package org.geoserver.restconfig.client;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.geoserver.restconfig.api.v1.client.FeaturetypesApi;
import org.geoserver.restconfig.api.v1.mapper.FeatureTypeResponseMapper;
import org.geoserver.restconfig.api.v1.model.FeatureTypeInfoWrapper;
import org.geoserver.restconfig.api.v1.model.FeatureTypeList;
import org.geoserver.restconfig.api.v1.model.FeatureTypeResponseWrapper;
import org.geoserver.restconfig.api.v1.model.FeatureTypesListWrapper;
import org.geoserver.restconfig.api.v1.model.NamedLink;
import org.geoserver.restconfig.model.catalog.FeatureTypeInfo;
import org.mapstruct.factory.Mappers;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class FeatureTypesClient {

	private @NonNull GeoServerClient client;

	private FeatureTypeResponseMapper mapper = Mappers.getMapper(FeatureTypeResponseMapper.class);

	FeaturetypesApi api() {
		return client.api(FeaturetypesApi.class);
	}

	/**
	 * Creates a FT on the given workspace's default store; if {@code info} has a
	 * {@link FeatureTypeInfo#getStore() store} set, the {@code FeatureTypeInfo}
	 * object sent to the server will have its store removed ({@code info} is left
	 * untouched) to let geoserver resolve the default store.
	 */
	public FeatureTypeInfo createOnDefaultStore(@NonNull String workspaceName, @NonNull FeatureTypeInfo info) {
		if (info.getStore() != null) {
			log.info(
					"Unsetting FeatureTypeInfo store '{}' to make sure geoserver tries "
							+ "to create it on the default store for workspace {}",
					info.getStore().getName(), workspaceName);
			info = client.clone(info);
			info.setStore(null);
		}
		api().createFeatureType(workspaceName, new FeatureTypeInfoWrapper().featureType(info));

		FeatureTypeResponseWrapper wrapper = api().getFeatureTypeByDefaultStore(workspaceName, info.getName(), true);
		return mapper.map(wrapper.getFeatureType());
	}

	/**
	 * Creates a FT on the given workspace and store; if {@code info} has a
	 * {@link FeatureTypeInfo#getStore() store} set whose name differs from
	 * {@code storeName}, the {@code FeatureTypeInfo} object sent to the server will
	 * have its store removed ({@code info} is left untouched)
	 * 
	 * @return
	 */
	public FeatureTypeInfo create(@NonNull String workspaceName, @NonNull String storeName,
			@NonNull FeatureTypeInfo info) {
		if (info.getStore() != null && !storeName.equals(info.getStore().getName())) {
			log.info(
					"Unsetting FeatureTypeInfo store '{}' to make sure geoserver creates it on the requested store '{}'",
					info.getStore().getName(), storeName);
			info = client.clone(info);
			info.setStore(null);
		}
		api().createFeatureTypeOnStore(workspaceName, storeName, new FeatureTypeInfoWrapper().featureType(info));
		return getFeatureType(workspaceName, storeName, info.getName()).get();
	}

	/**
	 * @param workspaceName name of workspace where to create the FT
	 * @param info          must have at least its name or nativeName and the store
	 *                      with the store name
	 * @return
	 */
	public FeatureTypeInfo create(@NonNull String workspaceName, @NonNull FeatureTypeInfo info) {
		if (null == info.getStore()) {
			throw new IllegalArgumentException("Target store not provided");
		}
		if (info.getNativeName() == null) {
			throw new IllegalArgumentException("GeoServer 2.15.2 requires nativeName to be set (due to a bug)");
		}
		if (info.getName() == null) {// again, 2.15.2 bug
			info.setName(info.getNativeName());
		}
		String storeName = info.getStore().getName();
		api().createFeatureTypeOnStore(workspaceName, storeName, new FeatureTypeInfoWrapper().featureType(info));
		return getFeatureType(workspaceName, info.getStore().getName(), info.getName()).get();
	}

	public FeatureTypeInfo update(@NonNull String workspaceName, @NonNull String currentName,
			@NonNull FeatureTypeInfo info) {
		if (null == info.getStore()) {
			throw new IllegalArgumentException("Target store not provided");
		}
		if (info.getNativeName() == null) {
			throw new IllegalArgumentException("GeoServer 2.15.2 requires nativeName to be set (due to a bug)");
		}
		if (info.getName() == null) {// again, 2.15.2 bug
			info.setName(info.getNativeName());
		}
		String storeName = info.getStore().getName();
		FeatureTypeInfoWrapper requestBody = new FeatureTypeInfoWrapper().featureType(info);
		api().modifyFeatureTypeByStore(workspaceName, storeName, currentName, requestBody, (List<String>) null);
		return getFeatureType(workspaceName, info.getStore().getName(), info.getName()).get();
	}

	public Optional<FeatureTypeInfo> getFeatureType(@NonNull String workspace, @NonNull String store,
			@NonNull String featureType) {

		try {
			FeatureTypeResponseWrapper wrapper = api().getFeatureType(workspace, store, featureType, true);
			return Optional.of(mapper.map(wrapper.getFeatureType()));
		} catch (ServerException.NotFound nf) {
			return Optional.empty();
		}
	}

	public List<NamedLink> findFeatureTypes(@NonNull String workspace, @NonNull String storeName) {
		List<NamedLink> typeLinks = Collections.emptyList();

		FeatureTypeList featureTypesByStore = null;
		try {
			String list = null;
			featureTypesByStore = api().getFeatureTypesByStore(workspace, storeName, list, Boolean.TRUE);
		} catch (Exception e) {
			log.debug("Got api error due to geoserver incompatible encoding of empty lists: {}", e.getMessage());
		}
		if (featureTypesByStore != null) {
			FeatureTypesListWrapper featureTypes = featureTypesByStore.getFeatureTypes();
			if (featureTypes != null) {
				typeLinks = featureTypes.getFeatureType();
			}
		}
		return typeLinks;
	}
}

package com.yatra.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.Objects;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.yatra.corporate.profile.service.beans.CompleteCorporateUserProfileWO;
import com.yatra.export.manager.converters.CorporateUserManagerConverter;
import com.yatra.export.mybatis.mapper.beans.ClientConfigDO;
import com.yatra.export.mybatis.mapper.beans.CorporateUserDO;
import com.yatra.galileo.common.beans.Status;
import com.yatra.galileo.corporate.client.manager.beans.CorporateGroupBO;
import com.yatra.galileo.corporate.user.manager.beans.AdditionalData;
import com.yatra.galileo.corporate.user.manager.beans.BillingEntityBO;
import com.yatra.galileo.corporate.user.manager.beans.CorporateUserBO;
import com.yatra.galileo.corporate.user.manager.enums.CorporateUserCreateOrUpdateStatus;
import com.yatra.galileo.service.connectors.CorporateProfileServiceConnector;
import com.yatra.galileo.service.connectors.CorporateUserProfileServiceConnector;
import com.yatra.galileo.service.connectors.SingleSignOnServiceConnector;
import com.yatra.gds.common.beans.ValidationResponseBO;
import com.yatra.gds.constants.AmadeusConstants;
import com.yatra.gds.constants.CommonConstants;
import com.yatra.gds.constants.CorporateUserConstants;
import com.yatra.sso.service.corporate.beans.CorporateUserWO;

@Component
public class CreateUserScriptManager{
	
	public static CorporateProfileServiceConnector cpsConnector;
	public static CorporateUserProfileServiceConnector cupsConnector;
	public static SingleSignOnServiceConnector ssoConnector;
	public static CorporateUserManagerConverter converter;
	
	public static CorporateUserCreateOrUpdateStatus updateCompleteUserInfo(List<Long> userIds, Map<Long, CorporateUserBO> usersMap) {
		Map<Long, CompleteCorporateUserProfileWO> userProfiles = cupsConnector.getCorporateUsersData(userIds);
		if (CollectionUtils.isEmpty(userProfiles)) {
			System.out.println("Failed to retrieve user profile data from CUPS for users" + userIds.toString());
			return CorporateUserCreateOrUpdateStatus.FAILURE_CREATE_OR_UPDATE_CORPORATE_USER_PROFILE_NO_PROFILE_RECORD_FOUND;
		}
		Map<Long, CorporateUserWO> userAccounts = ssoConnector.getCorporateUsers(userIds);
		if (CollectionUtils.isEmpty(userAccounts)) {
			System.out.println("Failed to retrieve user account data from SSO for users "+ userIds.toString());
			return CorporateUserCreateOrUpdateStatus.FAILURE_CREATE_OR_UPDATE_CORPORATE_USER_PROFILE_NO_ACCOUNT_RECORD_FOUND;
		}
		Map<Long, AdditionalData> additionalData = cupsConnector.getCorporateUsersAdditionalData(userIds);
		List<Long> billingEntityIds = getBillingEntityIds(additionalData);
		Map<Long, BillingEntityBO> billingIdsVsEntityMap = cpsConnector.getBillingEntitiesForIds(billingEntityIds);

		mergeProfileAndAccountData(usersMap, userProfiles, userAccounts, billingIdsVsEntityMap, additionalData);

		// Setting Band details
		Map<Long, Long> userVsGroupData = cupsConnector.getUserGroupMappingData(userIds);
		if (MapUtils.isNotEmpty(userVsGroupData)) {
			List<Long> groupIds = new ArrayList<>(userVsGroupData.values());
			Map<Long, CorporateGroupBO> groupIdVsGroupsMap = cpsConnector.getCorporateGroupsForIds(groupIds);
			if (MapUtils.isNotEmpty(usersMap) && MapUtils.isNotEmpty(groupIdVsGroupsMap)) {
				for (Long userId : usersMap.keySet()) {
					CorporateUserBO userBO = usersMap.get(userId);
					Long groupId = userVsGroupData.get(userId);
					if (groupId != null) {
						CorporateGroupBO groupBO = groupIdVsGroupsMap.get(groupId);
						userBO.setGroupData(groupBO);
					}
				}
			}
		}
		return CorporateUserCreateOrUpdateStatus.SUCCESS_CREATE_OR_UPDATE_CORPORATE_USER_PROFILE;
	}
	
	private static List<Long> getBillingEntityIds(Map<Long, AdditionalData> additionalData) {
		List<Long> billingEntities = null;
		if (!CollectionUtils.isEmpty(additionalData)) {
			for (Long userId : additionalData.keySet()) {
				if (Objects.nonNull(additionalData.get(userId))
						&& Objects.nonNull(additionalData.get(userId).getBillingEntity())) {
					if (billingEntities == null) {
						billingEntities = new ArrayList<Long>();
					}
					billingEntities.add(additionalData.get(userId).getBillingEntity());
				}
			}
		}
		return billingEntities;
	}
	
	private static Map<Long, CorporateUserBO> mergeProfileAndAccountData(Map<Long, CorporateUserBO> usersMap,
			Map<Long, CompleteCorporateUserProfileWO> userProfiles, Map<Long, CorporateUserWO> userAccounts,
			Map<Long, BillingEntityBO> billingIdVsEntityMap, Map<Long, AdditionalData> additionalData) {
		for (Long userId : userProfiles.keySet()) {
			usersMap.put(userId,
					convert(userProfiles.get(userId), userAccounts.get(userId), billingIdVsEntityMap, additionalData));
		}
		return usersMap;
	}
	
	private static CorporateUserBO convert(CompleteCorporateUserProfileWO corporateUserCompleteData,
			CorporateUserWO corporateUserWO, Map<Long, BillingEntityBO> billingIdVsEntityMap,
			Map<Long, AdditionalData> additionalDataMap) {
		CorporateUserBO userBO = new CorporateUserBO();
		if (Objects.nonNull(corporateUserCompleteData)) {
			if (Objects.nonNull(corporateUserCompleteData.getUserProfile())) {
				userBO.setUserProfileData(converter.convertUserProfile(corporateUserCompleteData.getUserProfile()));
			}
			if (Objects.nonNull(corporateUserCompleteData.getAdditionalDetails())) {
				userBO.setAdditionalDetails(corporateUserCompleteData.getAdditionalDetails());
			}
			if (Objects.nonNull(corporateUserCompleteData.getUserAddresses())) {
				userBO.setUserAddressData(converter.convertUserAddresses(corporateUserCompleteData));
			}
			if (Objects.nonNull(corporateUserCompleteData.getUserPassport())) {
				userBO.setUserPassportData(converter.convertUserPassport(corporateUserCompleteData.getUserPassport()));
			}
			if (Objects.nonNull(corporateUserCompleteData.getUserPreferences())) {
				userBO.setUserPreferenceData(
						converter.convertUserPreferences(corporateUserCompleteData.getUserPreferences()));
			}
			if (Objects.nonNull(corporateUserCompleteData.getUserMembership())) {
				userBO.setUserMembershipData(
						converter.convertUserMembership(corporateUserCompleteData.getUserMembership()));
			}
		}
		if (Objects.nonNull(corporateUserWO)) {
			if (Objects.nonNull(userBO.getUserProfileData())) {
				userBO.getUserProfileData().setClientId(corporateUserWO.getClientId());
				userBO.getUserProfileData().setEmailId(corporateUserWO.getEmailId());
				userBO.getUserProfileData().setStatus(corporateUserWO.getStatus());
				if (corporateUserWO.getMobileWO() != null) {
					userBO.getUserProfileData().setMobileNumber(corporateUserWO.getMobileWO().getMobileNumber());
					userBO.getUserProfileData().setIsdCode(corporateUserWO.getMobileWO().getIsdCode());
				}
			}
		}
		if (!CollectionUtils.isEmpty(additionalDataMap)
				&& Objects.nonNull(additionalDataMap.get(userBO.getUserProfileData().getUserId()))) {
			AdditionalData additionalData = additionalDataMap.get(userBO.getUserProfileData().getUserId());
			if (Objects.nonNull(additionalData.getBillingEntity()) && !CollectionUtils.isEmpty(billingIdVsEntityMap)) {
				userBO.setBillingEntity(billingIdVsEntityMap.get(additionalData.getBillingEntity()));
			}
			if (Objects.nonNull(additionalData.getDateOfBirth())) {
				userBO.getUserProfileData().setDateOfBirth(additionalData.getDateOfBirth());
			}
		}

		return userBO;
	}
	
	public static CorporateUserCreateOrUpdateStatus createOrUpdateUsersInAmadeus(Map<Long, CorporateUserBO> usersMap, List<Long> userIds, Long clientId,
			Map<Long, CorporateUserDO> alreadyExistingUsers) {
		
			for(Entry<Long, CorporateUserBO> e : usersMap.entrySet()) {
				System.out.println(e.getValue().toString());
			}
			int noOfThreads = (userIds.size())%1000 + 1;
			ExecutorService executorService = Executors.newFixedThreadPool(noOfThreads);
			List<List<Long>> partitionedLists = cupsConnector.partitionIntoBatches(userIds,CorporateUserConstants.USERS_PER_THREAD);
			List<FutureTask<Boolean>> futureTaskList = new ArrayList<FutureTask<Boolean>>();
			try {				
				for(int i=0;i<partitionedLists.size();i++) {
					List<Long> usersList = partitionedLists.get(i);
					FutureTask<Boolean> futureTask = null;
					if(i == 0)
						CreateUserScriptSession.currentSession = CreateUserScriptSession.TRANSACTION_STATUS_CODE_START;
					else if(i==partitionedLists.size()-1)
						CreateUserScriptSession.currentSession = CreateUserScriptSession.TRANSACTION_STATUS_CODE_END;
					else
						CreateUserScriptSession.currentSession = CreateUserScriptSession.TRANSACTION_STATUS_CODE_IN_SERIES;
					futureTask = (FutureTask<Boolean>) populateUserData(usersList,usersMap, alreadyExistingUsers);
					executorService.submit(futureTask);
					futureTaskList.add(futureTask);
				}
			} catch (Exception e) {
				executorService.shutdown();
				return CorporateUserCreateOrUpdateStatus.FAILURE_CREATE_OR_UPDATE_CORPORATE_USER_PROFILE_AMADEUS;
			}
			executorService.shutdown();
			return null;
	}
	
	private static FutureTask<Boolean> populateUserData(List<Long> usersList, Map<Long, CorporateUserBO> usersMap, Map<Long, CorporateUserDO> alreadyExistingUsers) {
		FutureTask<Boolean> futuretask = new FutureTask<Boolean>(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				Map<Long, Boolean> shouldUpdateClientDetailsMap = new HashMap<Long, Boolean>();
				String gds = CommonConstants.GDS_AMADEUS;
				for (Long userId : usersList) {
					CorporateUserBO userBO = usersMap.get(userId);
					ClientConfigDO clientConfig = CreateUserScriptDao.getClientConfig(userBO.getUserProfileData().getClientId(), gds);
					Boolean shouldUpdateClientDetails = checkClientConfigForUser(userId, userBO, clientConfig, gds,shouldUpdateClientDetailsMap);
					if (shouldUpdateClientDetails) {
						userBO.setPcc(clientConfig.getPcc());
						ValidationResponseBO validationResponseBO = new ValidationResponseBO();
						createOrUpdateUserProfile(alreadyExistingUsers, userId, userBO, validationResponseBO, false);
						CreateUserScriptDao.updateUploadStatusinDb(userId, gds, userBO, validationResponseBO);
						if ((Status.CREATION_FAILURE.equals(validationResponseBO.getStatus())
								|| Status.UPDATION_FAILURE.equals(validationResponseBO.getStatus()))
								&& (StringUtils.isNotBlank(validationResponseBO.getMessage())
										&& (validationResponseBO.getMessage().contains(AmadeusConstants.SESSION_ERROR)
												|| validationResponseBO.getMessage()
														.contains(AmadeusConstants.INCORRECT_VERSION_ERROR)))) {
							retryProfileSync(alreadyExistingUsers, userId, userBO,validationResponseBO);
						}
					} else {
						System.out.println("Pushing data to Amadeus not allowed for user {}" + userId);
					} 	
				}
				return true;
			}
	});
		return futuretask;
	}
		
	public static ValidationResponseBO createOrUpdateUserProfile(Map<Long, CorporateUserDO> alreadyExistingUsers, Long userId,
			CorporateUserBO userBO, ValidationResponseBO responseBO, boolean isRetry) {
		if (Objects.nonNull(alreadyExistingUsers) && Objects.nonNull(alreadyExistingUsers.get(userId))
				&& !Objects.isNull(alreadyExistingUsers.get(userId).getVersion())) {
			responseBO = CreateUserScriptUpdateOperation.updateUserProfile(userBO, responseBO, isRetry);
			System.out.println("Status from Amadeus for user" +userId + "\t"+responseBO);
		} else {
			responseBO = CreateUserScriptCreateOperation.createUserProfile(userBO, responseBO);
			System.out.println("Status from Amadeus for user" +userId + "\t"+responseBO);
		}
		return responseBO;
	}
		
	private static void retryProfileSync(Map<Long, CorporateUserDO> alreadyExistingUsers, Long userId, CorporateUserBO userBO, ValidationResponseBO validationResponseBO) {
		System.out.println("retrying amadeus profile sync for user {} due to error {}"+userId+"\t"+validationResponseBO.getMessage());
		ValidationResponseBO newValidationResponseBO = new ValidationResponseBO();
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				if (StringUtils.isNotBlank(validationResponseBO.getMessage())
						&& validationResponseBO.getMessage().contains(AmadeusConstants.INCORRECT_VERSION_ERROR)) {
					createOrUpdateUserProfile(alreadyExistingUsers, userId, userBO, newValidationResponseBO, true);
				} else {
					createOrUpdateUserProfile(alreadyExistingUsers, userId, userBO, newValidationResponseBO, false);
				}
				CreateUserScriptDao.updateUploadStatusinDb(userId, CommonConstants.GDS_AMADEUS, userBO, newValidationResponseBO);
				
				if (Status.CREATION_SUCCESS.equals(newValidationResponseBO.getStatus())
						|| Status.UPDATION_SUCCESS.equals(newValidationResponseBO.getStatus())) {
					System.out.println("Successfully synced amadeus profile with userId {} after retry."+ userId);
				} else {
					System.out.println("Unable to create/update amadeus profile  for user {} due to error {} after retry"+
							userId+"\t"+ newValidationResponseBO.getMessage());
				}
			}
		});
		thread.start();
	}
		
		public static Boolean checkClientConfigForUser(Long userId, CorporateUserBO userBO, ClientConfigDO clientConfig, String gds,
				Map<Long, Boolean> shouldUpdateClientDetailsMap) {
			Boolean shouldUpdateClientDetails = false;
			Boolean valueForClient = shouldUpdateClientDetailsMap.get(userBO.getUserProfileData().getClientId());
			if (Objects.nonNull(valueForClient)) {
				shouldUpdateClientDetails = valueForClient;
			} else if (Objects.nonNull(clientConfig)) {
				shouldUpdateClientDetails = clientConfig.getPushUserProfile();
			}
			shouldUpdateClientDetailsMap.put(userBO.getUserProfileData().getClientId(), shouldUpdateClientDetails);
			return shouldUpdateClientDetails;
		}
}

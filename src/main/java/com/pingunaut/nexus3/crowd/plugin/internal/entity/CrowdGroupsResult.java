package com.pingunaut.nexus3.crowd.plugin.internal.entity;

import java.util.ArrayList;
import java.util.List;

public class CrowdGroupsResult {

	private List<CrowdGroupResult> groups = new ArrayList<>();

	public List<CrowdGroupResult> getGroups() {
		return groups;
	}

	public void setGroups(List<CrowdGroupResult> groups) {
		this.groups = groups;
	}
	
}

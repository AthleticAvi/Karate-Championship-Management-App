package com.management.dto;

import java.util.List;
import java.util.Map;

public class KumiteGameRequestDTO {
    private Map<String, PlayerDTO> playersMap;
    private List<String> refereeList;

    public Map<String, PlayerDTO> getPlayersMap() {
        return playersMap;
    }

    public void setPlayersMap(Map<String, PlayerDTO> playersMap) {
        this.playersMap = playersMap;
    }

    public List<String> getRefereeList() {
        return refereeList;
    }

    public void setRefereeList(List<String> refereeList) {
        this.refereeList = refereeList;
    }
}
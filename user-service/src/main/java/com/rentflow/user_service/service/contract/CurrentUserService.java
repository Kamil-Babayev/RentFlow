package com.rentflow.user_service.service.contract;

import java.util.List;

public interface CurrentUserService {

    String getUserId();

    String getEmail();

    String getFirstName();

    String getLastName();

    List<String> getRoles();

}

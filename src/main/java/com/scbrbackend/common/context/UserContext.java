package com.scbrbackend.common.context;

public class UserContext {

    private static final ThreadLocal<CurrentUser> USER_THREAD_LOCAL = new ThreadLocal<>();

    public static void setCurrentUser(CurrentUser user) {
        USER_THREAD_LOCAL.set(user);
    }

    public static CurrentUser getCurrentUser() {
        return USER_THREAD_LOCAL.get();
    }

    public static void removeCurrentUser() {
        USER_THREAD_LOCAL.remove();
    }
}

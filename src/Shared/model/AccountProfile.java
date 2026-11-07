package Shared.model;

public record AccountProfile(String name, String displayName, User.Role role, boolean locked) {}

package com.huawei.ascend.examples.workmate.office;

public class SkillNotFoundException extends RuntimeException {

    public SkillNotFoundException(String skillId) {
        super("Skill not found: " + skillId);
    }
}

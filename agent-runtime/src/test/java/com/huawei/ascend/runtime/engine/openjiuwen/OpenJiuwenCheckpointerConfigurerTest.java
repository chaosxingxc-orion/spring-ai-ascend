package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenJiuwenCheckpointerConfigurerTest {

    private final Checkpointer original = CheckpointerFactory.getCheckpointer();

    @AfterEach
    void restoreOriginalCheckpointer() {
        CheckpointerFactory.setDefaultCheckpointer(original);
    }

    @Test
    void setDefaultInstallsProvidedOpenJiuwenCheckpointer() {
        Checkpointer checkpointer = new InMemoryCheckpointer();

        Checkpointer installed = OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer);

        assertThat(installed).isSameAs(checkpointer);
        assertThat(CheckpointerFactory.getCheckpointer()).isSameAs(checkpointer);
    }

    @Test
    void setInMemoryDefaultInstallsNewInMemoryCheckpointer() {
        Checkpointer installed = OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();

        assertThat(installed).isInstanceOf(InMemoryCheckpointer.class);
        assertThat(CheckpointerFactory.getCheckpointer()).isSameAs(installed);
    }

    @Test
    void setDefaultRejectsNullCheckpointer() {
        assertThatNullPointerException()
                .isThrownBy(() -> OpenJiuwenCheckpointerConfigurer.setDefault(null))
                .withMessageContaining("checkpointer");
    }
}

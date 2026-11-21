#include <gtest/gtest.h>

#include <cstring>

#include "jakotata/version.h"

TEST(VoiceCore, ReportsVersion) {
    const char* v = jakotata::voiceCoreVersion();
    ASSERT_NE(v, nullptr);
    EXPECT_STREQ(v, "0.1.0");
}

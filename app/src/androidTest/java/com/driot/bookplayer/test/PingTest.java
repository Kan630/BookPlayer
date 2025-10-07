package com.driot.bookplayer.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PingTest {
  @Test
  public void ping() {
    Context ctx = ApplicationProvider.getApplicationContext();
    assertThat(ctx.getPackageName(), is("com.driot.bookplayer.debug"));
  }
}

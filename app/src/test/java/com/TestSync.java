package com;

import com.foobnix.model.MyPath;
import com.foobnix.model.SimpleMeta;

import android.os.Environment;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class TestSync {
    private static MockedStatic<Environment> environment;

    @BeforeClass public static void provideStorageForJvm() {
        // android.jar has no device storage. Supply it before MyPath's static initialization.
        environment = Mockito.mockStatic(Environment.class);
        environment.when(Environment::getExternalStorageDirectory)
                .thenReturn(new File("/storage/emulated/0"));
    }

    @AfterClass public static void releaseStorageStub() {
        environment.close();
    }

    @Test
    public void mergeMyPath() {
       assertEquals("1",MyPath.toAbsolute("1"));
    }

    @Test
    public void merge1() {
        SimpleMeta s1 = new SimpleMeta("2", 2);
        SimpleMeta s2 = new SimpleMeta("2", 3);

        //assertEquals(s2, ExportConverter.merge(s1, s2));
    }

    @Test
    public void merge2() {
        SimpleMeta s1 = new SimpleMeta("2", 3);
        SimpleMeta s2 = new SimpleMeta("2", 2);

        //assertEquals(s1, ExportConverter.merge(s1, s2));
    }

    @Test
    public void merge3() {
        SimpleMeta s1 = new SimpleMeta("2", 2);
        SimpleMeta s2 = new SimpleMeta("2", 2);

        //assertEquals(s1, ExportConverter.merge(s1, s2));
    }



}

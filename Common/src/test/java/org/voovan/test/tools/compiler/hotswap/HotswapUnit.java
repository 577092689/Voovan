package org.voovan.test.tools.compiler.hotswap;

import junit.framework.TestCase;
import org.voovan.tools.TEnv;
import org.voovan.tools.TObject;
import org.voovan.tools.compiler.DynamicCompiler;
import org.voovan.tools.compiler.hotswap.Hotswaper;
import org.voovan.tools.log.Logger;

/**
 * 类文字命名
 *
 * @author: helyho
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class HotswapUnit extends TestCase{

    public static void main(String[] args) throws Exception {

        Hotswaper hotSwaper = new Hotswaper();

        System.out.println("=============Run=============");
        for (int i = 0; i < 2; i++) {
            System.out.println("\r\n=============Run " + i + "=============");
            long startTime = System.currentTimeMillis();
            TestSay testSay = new TestSay();
            Object result = testSay.say();
            Logger.info("==>RunTime: " + (System.currentTimeMillis() - startTime) + "\r\n==>Result: " + result);
            //运行脚本
            TEnv.sleep(100);

            hotSwaper.autoReloadClass();

            if (i == 0) {
                DynamicCompiler dynamicCompiler = new DynamicCompiler();
                dynamicCompiler.compileCode(TObject.asList("/Users/helyho/Work/Java/Voovan/Common/src/test/java/org/voovan/test/tools/compiler/hotswap/TestSay.java"),
                        "/Users/helyho/Work/Java/Voovan/target/test-classes/Common/");
                hotSwaper.autoReloadClass();
            }

        }
    }
}
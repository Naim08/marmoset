package edu.umd.cs.diffText;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextDiff extends StringsWriter {

    /**
     * Invoke the main method of a class, redirecting System.in and System.out,
     * and passing the supplied arguments to main
     */
    public static void invokeMain(Class<?> c, InputStream input,
            OutputStream out, String... args) throws Exception {
        PrintStream oStream = new PrintStream(out);
        InputStream oldIn = System.in;
        PrintStream oldOut = System.out;
        try {
            Method main;

            try {
                main = c.getMethod("main", String[].class);

                setSystemInAndOut(input, oStream);

            } catch (Throwable t) {
                // If an exception happens before we execute student code, throw
                // a TestInfrastructureException
                throw new TestInfrastructureException(t);
            }

            main.invoke(null, (Object) args);
            oStream.close();
            input.close();
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof Exception)
                throw (Exception) t;
            if (t instanceof Error)
                throw (Error) t;
            throw e;
        } finally {
            setSystemInAndOut(oldIn, oldOut);

        }
    }

    private static void setSystemInAndOut(final InputStream input,
            final PrintStream oStream) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                System.setIn(input);
                System.setOut(oStream);
                return null;
            };
        });
    }

    public static Builder withOptions() {
        return new Builder();
    }

    public static Builder withOptions(EnumSet<Option> options) {
        return new Builder(options);
    }

    public enum Option {
        TRIM, IGNORES_CASE, IGNORE_WHITESPACE_CHANGE;
        public static Option valueOfAnyCase(String name) {
            name = name.toUpperCase();
            if (name.equals("IGNORESCASE"))
                return IGNORES_CASE;
            if (name.equals("IGNOREWHITESPACECHANGE"))
                return IGNORE_WHITESPACE_CHANGE;
            return valueOf(name);
        }
    };

    public static class Builder implements Cloneable {
        final EnumSet<Option> options;

        final ArrayDeque<Object> expect = new ArrayDeque<Object>();

        Builder() {
            options = EnumSet.noneOf(Option.class);
        }

        Builder(EnumSet<Option> options) {
            this.options = EnumSet.copyOf(options);
        }

        protected Builder clone() {
            try {
                return (Builder) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        public Builder copy() {
            return this.clone();
        }

        public Builder set(Option... options) {

            for (Option o : options) {
                this.options.add(o);

            }
            return this;
        }

        public Builder trim() {
            set(Option.TRIM);
            return this;
        }

        public Builder ignoreCase() {
            set(Option.IGNORES_CASE);
            return this;
        }

        public Builder ignoreWhitespaceChange() {
            set(Option.IGNORE_WHITESPACE_CHANGE);
            return this;
        }

        public Builder expect(String s) {
            expect.add(s);
            return this;
        }

        public Builder expect(String... strings) {
            for (String s : strings)
                expect.add(s);
            return this;
        }

        public Builder expect(Iterable<String> strings) {
            for (String s : strings)
                expect.add(s);
            return this;
        }

        public Builder expect(Pattern p) {
            expect.add(p);
            return this;
        }

        public Builder expect(File f) {
            expect.add(f);
            return this;
        }

        public TextDiff build() {
            return new TextDiff(new ArrayDeque<Object>(expect), options);

        }

        /**
         * Check that the output of the testedMain method matches that of the
         * referenceMain method on the supplied input file.
         * 
         * @param testedMain
         *            The class of the main method to be tested.
         * @param referenceMain
         *            The class of the reference main method
         * @param inputFile
         *            the input file
         * @throws Exception
         *             - if if any exception is thrown by the tested code.
         */
        public void check(Class<?> testedMain, Class<?> referenceMain,
                String inputFile) throws Exception {
            File f;
            Builder b = copy();
            try {
                StringListWriter expectedWriter = new StringListWriter();
                f = new File(inputFile);

                invokeMain(referenceMain, new FileInputStream(f),
                        expectedWriter);
                b.expect(expectedWriter.getStrings());
            } catch (Throwable t) {
                throw new TestInfrastructureException(t);
            }

            invokeMain(testedMain, new FileInputStream(f), b.build());
        }

        /**
         * Check that the output of the testedMain method matches that of the
         * referenceMain method on the supplied input file.
         * 
         * @param testedMain
         *            The class of the main method to be tested.
         * @param referenceMain
         *            The class of the reference main method
         * @param inputFile
         *            the input file
         * @throws Exception
         *             - if if any exception is thrown by the tested code.
         */
        public void check(Class<?> testedMain, String inputFile,
                String expectedOutputFile) throws Exception {
            Builder b = copy();
            try {
                b.expect(new File(expectedOutputFile));
            } catch (Throwable t) {
                throw new TestInfrastructureException(t);
            }
            invokeMain(testedMain, new FileInputStream(new File(inputFile)),
                    b.build());
        }
    }

    public static class TestInfrastructureException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TestInfrastructureException(Throwable t) {
            super(t);
        }

        TestInfrastructureException(String msg) {
            super(msg);
        }
    }

    final EnumSet<Option> options;
    final ArrayDeque<Object> expect;

    TextDiff(ArrayDeque<Object> expect, EnumSet<Option> options) {
        this.expect = expect;
        this.options = EnumSet.copyOf(options);

    }

    private String normalize(String s) {
        if (options.contains(Option.TRIM))
            s = s.trim();
        if (options.contains(Option.IGNORES_CASE))
            s = s.toLowerCase();
        if (options.contains(Option.IGNORE_WHITESPACE_CHANGE))
            s = s.replaceAll("\\s+", " ");
        return s;
    }

    private Object getExpected() {
        try {
            while (true) {
                Object o = expect.pollFirst();
                if (o == null)
                    return null;
                Class<?> c = o.getClass();
                if (c == String.class) {
                    return normalize((String) o);
                } else if (c == Pattern.class) {
                    return c;
                } else if (c == BufferedReader.class) {
                    BufferedReader r = (BufferedReader) o;
                    String txt = r.readLine();
                    if (txt == null)
                        continue;
                    expect.addFirst(r);
                    return normalize(txt);

                } else if (c == File.class) {
                    BufferedReader r = new BufferedReader(new FileReader(
                            (File) o));
                    expect.addFirst(r);
                } else {
                    throw new AssertionError("Did not expect " + c);
                }
            }
        } catch (Throwable t) {
            throw new TestInfrastructureException(t);
        }

    }

    protected void got(int line, String txt) {
        System.out.printf("got line %d : %s%n", line, txt);
        txt = normalize(txt);
        Object o = getExpected();
        if (o == null) {
            fail("Didn't expect any more output but got " + txt);
        } else if (o instanceof String) {
            assertEquals("On line " + line, o, txt);
        } else if (o instanceof Pattern) {
            Pattern p = (Pattern) o;
            Matcher m = p.matcher(txt);
            assertTrue("On line" + line + "," + p + " doesn't match " + txt,
                    m.matches());
        } else {
            fail("Got " + o.getClass());
        }

    }

    @Override
    public void close() {
        super.close();
        Object o = getExpected();
        if (o == null)
            return;
        fail("no more output; expected " + o);
    }

    public static FutureTask<Void> copyTask(final String name, final InputStream from,
            final OutputStream to) {
        Callable<Void> doCheck = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                System.out.println("copying input stream in " + name);

                byte[] buf = new byte[1024];
                try {
                    while (true) {
                        int sz;
                        try {
                            sz = from.read(buf);
                        } catch (Exception e) {
                            // ignore exceptions that happen while reading, but
                            // treat them as EOF
                            System.err.println("Error in " + name);
                            e.printStackTrace();
                            sz = -1;
                        }
                        if (sz < 0) {
                            to.close();
                            return null;
                        }
                        to.write(buf, 0, sz);

                    }
                } finally {
                    try {
                        from.close();
                        to.close();
                    } catch (Exception e) {
                        System.err.println("Error in " + name);
                        e.printStackTrace();
                        assert true;
                    }

                }
            }
        };

        return new FutureTask<Void>(doCheck);
    }

    public static FutureTask<Void> copyTask(final String name, final Reader from, final Writer to) {
        Callable<Void> doCheck = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                char[] buf = new char[1024];
                try {
                    while (true) {
                        int sz;
                        try {
                            sz = from.read();
                        } catch (Exception e) {
                            // ignore exceptions that happen while reading, but
                            // treat them as EOF
                            System.err.println("Error in " + name);
                            e.printStackTrace();
                            sz = -1;
                        }
                        if (sz < 0) {
                            to.close();
                            return null;
                        }
                        to.write(buf, 0, sz);
                    }
                } finally {
                    try {
                        from.close();
                        to.close();
                    } catch (Exception e) {
                        System.err.println("Error in " + name);
                        e.printStackTrace();
                        assert true;
                    }

                }
            }
        };

        return new FutureTask<Void>(doCheck);
    }

    public FutureTask<Void> check(final InputStream actual) {
        return copyTask("TextDiffCheck", actual, this);
    }

}

package com.google.caliper.runner;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;
import com.google.common.collect.Lists;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Integration tests for misbehaving benchmarks.
 */
@RunWith(JUnit4.class)
public class BadUserCodeTest {
  @Rule public CaliperTestWatcher runner = new CaliperTestWatcher();

  @Test

  public void testExceptionInInit() throws Exception {
    try {
      runner.forBenchmark(ExceptionInInitBenchmark.class).run();
      fail();
    } catch (UserCodeException expected) {}
  }

  private static void throwSomeUserException() {
    throw new RuntimeException();
  }

  static class ExceptionInInitBenchmark {
    static {
      throwSomeUserException();
    }

    @Benchmark void timeSomething(int reps) {
      fail("" + reps);
    }
  }

  @Test

  public void testExceptionInConstructor() throws Exception {
    try {
      runner.forBenchmark(ExceptionInConstructorBenchmark.class).run();
      fail();
    } catch (UserCodeException expected) {}
  }

  static class ExceptionInConstructorBenchmark {
    ExceptionInConstructorBenchmark() {
      throw new RuntimeException();
    }

    @Benchmark void timeSomething(int reps) {
      fail("" + reps);
    }
  }

  @Test

  public void testExceptionInMethod() throws Exception {
    try {
      runner.forBenchmark(ExceptionInMethodBenchmark.class).run();
      fail();
    } catch (UserCodeException expected) {}
  }

  static class ExceptionInMethodBenchmark {
    @Benchmark void timeSomething(int reps) {
      throw new RuntimeException();
    }
  }

  /**
   * This test requires a lot of memory and will fail tests when the build
   * machine does not have enough memory.
   * 
   * @throws Exception
   */
  @Test
  @Ignore
  public void testExceptionInMethod_notInDryRun() throws Exception {
    try {
      runner.forBenchmark(ExceptionLateInMethodBenchmark.class).run();
      fail();
    } catch (ProxyWorkerException expected) {
      assertTrue(expected.getMessage().contains(ExceptionLateInMethodBenchmark.class.getName()));
    }
  }

  static class ExceptionLateInMethodBenchmark {
    @Benchmark void timeSomething(int reps) {
      if (reps > 1) {
        throw new RuntimeException();
      }
    }
  }

  @Test

  public void testExceptionInSetUp() throws Exception {
    try {
      runner.forBenchmark(ExceptionInSetUpBenchmark.class).run();
      fail();
    } catch (UserCodeException expected) {}
  }

  static class ExceptionInSetUpBenchmark {
    @BeforeExperiment void setUp() {
      throw new RuntimeException();
    }

    @Benchmark void timeSomething(int reps) {
      fail("" + reps);
    }
  }

  /**
   * This test requires a lot of memory and will fail tests when the build
   * machine does not have enough memory.
   * 
   * @throws Exception
   */
  @Test
  @Ignore
  public void testNonDeterministicAllocation_noTrackAllocations() throws Exception {
    try {
      runner.forBenchmark(NonDeterministicAllocationBenchmark.class)
          .instrument("allocation")
          .options("-Cinstrument.allocation.options.trackAllocations=" + false)
          .run();
      fail();
    } catch (ProxyWorkerException expected) {
      String message = "Your benchmark appears to have non-deterministic allocation behavior";
      assertTrue("Expected " + expected.getMessage() + " to contain " + message,
          expected.getMessage().contains(message));
    }
  }

  /**
   * This test requires a lot of memory and will fail tests when the build
   * machine does not have enough memory.
   * 
   * @throws Exception
   */
  @Test
  @Ignore
  public void testNonDeterministicAllocation_trackAllocations() throws Exception {
    try {
      runner.forBenchmark(NonDeterministicAllocationBenchmark.class)
          .instrument("allocation")
          .options("-Cinstrument.allocation.options.trackAllocations=" + true)
          .run();
      fail();
    } catch (ProxyWorkerException expected) {
      String message = "Your benchmark appears to have non-deterministic allocation behavior";
      assertTrue("Expected " + expected.getMessage() + " to contain " + message,
          expected.getMessage().contains(message));
    }
  }

  /** The number of allocations is non deterministic because it depends on static state. */
  static class NonDeterministicAllocationBenchmark {
    static int timeCount = 0;
    // We dump items into this list so the jit cannot remove the allocations
    static List<Object> list = Lists.newArrayList();
    @Benchmark int timeSomethingFBZ(int reps) {
      timeCount++;
      if (timeCount % 2 == 0) {
        list.add(new Object());
        return list.hashCode();
      }
      return this.hashCode();
    }
  }

  @Test

  public void testComplexNonDeterministicAllocation_noTrackAllocations() throws Exception {
    // Without trackAllocations enabled this kind of non-determinism cannot be detected.
    runner.forBenchmark(ComplexNonDeterministicAllocationBenchmark.class)
        .instrument("allocation")
        .options("-Cinstrument.allocation.options.trackAllocations=" + false)
        .run();
  }

  @Test

  public void testComplexNonDeterministicAllocation_trackAllocations() throws Exception {
    try {
      runner.forBenchmark(ComplexNonDeterministicAllocationBenchmark.class)
          .instrument("allocation")
          .options("-Cinstrument.allocation.options.trackAllocations=" + true)
          .run();
    } catch (ProxyWorkerException expected) {
      String message = "Your benchmark appears to have non-deterministic allocation behavior";
      assertTrue("Expected " + expected.getMessage() + " to contain " + message,
          expected.getMessage().contains(message));
    }
  }

  /** Benchmark allocates the same number of things each time but in a different way. */
  static class ComplexNonDeterministicAllocationBenchmark {
    static int timeCount = 0;
    @Benchmark int timeSomethingFBZ(int reps) {
      // We dump items into this list so the jit cannot remove the allocations
      List<Object> list = Lists.newArrayList();
      timeCount++;
      if (timeCount % 2 == 0) {
        list.add(new Object());
      } else {
        list.add(new Object());
      }
      return list.hashCode();
    }
  }
}

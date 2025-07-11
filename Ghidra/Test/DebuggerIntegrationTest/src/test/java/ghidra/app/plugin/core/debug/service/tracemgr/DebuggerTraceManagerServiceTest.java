/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.debug.service.tracemgr;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

import java.util.*;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import db.Transaction;
import generic.test.category.NightlyCategory;
import ghidra.app.plugin.core.debug.gui.AbstractGhidraHeadedDebuggerIntegrationTest;
import ghidra.app.plugin.core.debug.service.control.DebuggerControlServicePlugin;
import ghidra.app.services.DebuggerControlService;
import ghidra.debug.api.control.ControlMode;
import ghidra.debug.api.target.Target;
import ghidra.debug.api.tracemgr.DebuggerCoordinates;
import ghidra.framework.model.DomainFile;
import ghidra.trace.database.target.DBTraceObjectManager;
import ghidra.trace.model.Lifespan;
import ghidra.trace.model.Trace;
import ghidra.trace.model.target.TraceObject;
import ghidra.trace.model.target.iface.TraceEventScope;
import ghidra.trace.model.target.path.KeyPath;
import ghidra.trace.model.thread.TraceThread;
import ghidra.trace.model.time.schedule.TraceSchedule;
import ghidra.trace.model.time.schedule.TraceSchedule.ScheduleForm;

@Category(NightlyCategory.class) // this may actually be an @PortSensitive test
public class DebuggerTraceManagerServiceTest extends AbstractGhidraHeadedDebuggerIntegrationTest {

	protected DebuggerControlService editingService;

	@Before
	public void setUpTraceManagerTest() throws Exception {
		addPlugin(tool, DebuggerControlServicePlugin.class);
		editingService = tool.getService(DebuggerControlService.class);
	}

	@Test
	public void testGetOpenTraces() throws Exception {
		assertEquals(Set.of(), traceManager.getOpenTraces());

		createAndOpenTrace();
		waitForDomainObject(tb.trace);

		assertEquals(Set.of(tb.trace), traceManager.getOpenTraces());

		traceManager.closeTrace(tb.trace);
		waitForSwing();

		assertEquals(Set.of(), traceManager.getOpenTraces());
	}

	@Test
	public void testGetCurrent() throws Exception {
		assertEquals(DebuggerCoordinates.NOWHERE, traceManager.getCurrent());

		createTrace();
		waitForDomainObject(tb.trace);

		assertEquals(DebuggerCoordinates.NOWHERE, traceManager.getCurrent());

		traceManager.openTrace(tb.trace);
		waitForSwing();

		assertEquals(DebuggerCoordinates.NOWHERE, traceManager.getCurrent());

		traceManager.activateTrace(tb.trace);
		waitForSwing();

		assertEquals(tb.trace, traceManager.getCurrent().getTrace());
	}

	@Test
	public void testGetCurrentView() throws Exception {
		assertNull(traceManager.getCurrentView());

		createTrace();
		waitForDomainObject(tb.trace);

		assertNull(traceManager.getCurrentView());

		traceManager.openTrace(tb.trace);
		waitForSwing();

		assertNull(traceManager.getCurrentView());

		traceManager.activateTrace(tb.trace);
		waitForSwing();

		assertEquals(tb.trace, traceManager.getCurrentView().getTrace());
		assertEquals(tb.trace.getProgramView(), traceManager.getCurrentView());
	}

	@Test
	public void testGetCurrentThread() throws Exception {
		assertNull(traceManager.getCurrentThread());

		createTrace();
		waitForDomainObject(tb.trace);

		assertNull(traceManager.getCurrentThread());

		traceManager.openTrace(tb.trace);
		waitForSwing();

		assertNull(traceManager.getCurrentThread());

		traceManager.activateTrace(tb.trace);
		waitForSwing();

		assertNull(traceManager.getCurrentThread());

		TraceThread thread;
		try (Transaction tx = tb.startTransaction()) {
			tb.createRootObject("Target");
			thread = tb.getOrAddThread("Threads[1]", 0);
		}
		waitForDomainObject(tb.trace);

		assertEquals(thread, traceManager.getCurrentThread());

		DebuggerTraceManagerServiceTestAccess.setEnsureActiveTrace(traceManager, false);
		traceManager.activateTrace(null);
		waitForSwing();

		assertNull(traceManager.getCurrentTrace());
		assertEquals(thread, traceManager.getCurrentFor(tb.trace).getThread());

		traceManager.closeTrace(tb.trace);
		waitForSwing();

		assertNull(traceManager.getCurrentFor(tb.trace).getThread());
	}

	@Test
	public void testGetCurrentSnap() throws Exception {
		assertEquals(0, traceManager.getCurrentSnap());

		createTrace();
		waitForDomainObject(tb.trace);

		assertEquals(0, traceManager.getCurrentSnap());

		traceManager.openTrace(tb.trace);
		waitForSwing();

		assertEquals(0, traceManager.getCurrentSnap());

		traceManager.activateTrace(tb.trace);
		waitForSwing();

		assertEquals(0, traceManager.getCurrentSnap());

		traceManager.activateSnap(5);
		waitForSwing();

		assertEquals(5, traceManager.getCurrentSnap());

		DebuggerTraceManagerServiceTestAccess.setEnsureActiveTrace(traceManager, false);
		traceManager.activateTrace(null);
		waitForSwing();

		assertEquals(0, traceManager.getCurrentSnap());
	}

	@Test
	public void testGetCurrentFrame() throws Exception {
		assertEquals(0, traceManager.getCurrentFrame());

		createTrace();
		waitForDomainObject(tb.trace);

		assertEquals(0, traceManager.getCurrentFrame());

		traceManager.openTrace(tb.trace);
		waitForSwing();

		assertEquals(0, traceManager.getCurrentFrame());

		traceManager.activateTrace(tb.trace);
		waitForSwing();

		assertEquals(0, traceManager.getCurrentFrame());

		traceManager.activateFrame(5);
		waitForSwing();

		assertEquals(5, traceManager.getCurrentFrame());

		DebuggerTraceManagerServiceTestAccess.setEnsureActiveTrace(traceManager, false);
		traceManager.activateTrace(null);
		waitForSwing();

		assertEquals(0, traceManager.getCurrentFrame());
	}

	@Test
	public void testGetCurrentObject() throws Exception {
		assertEquals(null, traceManager.getCurrentObject());

		createTrace();
		waitForDomainObject(tb.trace);

		assertEquals(null, traceManager.getCurrentObject());

		traceManager.openTrace(tb.trace);
		waitForSwing();

		assertEquals(null, traceManager.getCurrentObject());

		traceManager.activateTrace(tb.trace);
		waitForSwing();

		assertEquals(null, traceManager.getCurrentObject());

		TraceObject objThread0;
		try (Transaction tx = tb.startTransaction()) {
			DBTraceObjectManager objectManager = tb.trace.getObjectManager();
			tb.createRootObject();
			objThread0 = objectManager.createObject(KeyPath.parse("Targets[0].Threads[0]"));
		}
		// Manager listens for the root-created event to activate it. Wait for it to clear.
		waitForDomainObject(tb.trace);
		TraceThread thread =
			Objects.requireNonNull(objThread0.queryInterface(TraceThread.class));

		traceManager.activateObject(objThread0);
		waitForSwing();

		assertEquals(objThread0, traceManager.getCurrentObject());
		assertEquals(thread, traceManager.getCurrentThread());

		DebuggerTraceManagerServiceTestAccess.setEnsureActiveTrace(traceManager, false);
		traceManager.activateTrace(null);
		waitForSwing();

		assertEquals(null, traceManager.getCurrentObject());
	}

	@Test
	public void testOpenTrace() throws Exception {
		createTrace();
		waitForDomainObject(tb.trace);

		assertEquals(Set.of(), traceManager.getOpenTraces());
		assertEquals(Set.of(tb), Set.copyOf(tb.trace.getConsumerList()));

		traceManager.openTrace(tb.trace);
		waitForSwing();

		assertEquals(Set.of(tb, traceManager), Set.copyOf(tb.trace.getConsumerList()));
	}

	// TODO: Test the other close methods: all, others, dead

	@Test
	public void testOpenTraceDomainFile() throws Exception {
		createTrace();
		waitForDomainObject(tb.trace);

		assertEquals(Set.of(), traceManager.getOpenTraces());
		assertEquals(Set.of(tb), Set.copyOf(tb.trace.getConsumerList()));

		traceManager.openTrace(tb.trace.getDomainFile(), DomainFile.DEFAULT_VERSION);
		waitForSwing();

		assertEquals(Set.of(tb, traceManager), Set.copyOf(tb.trace.getConsumerList()));
	}

	@Test
	public void testOpenTraceDomainFileWrongType() throws Exception {
		createProgram();
		waitForDomainObject(program);

		assertEquals(Set.of(), traceManager.getOpenTraces());
		assertEquals(Set.of(this), Set.copyOf(program.getConsumerList()));

		try {
			traceManager.openTrace(program.getDomainFile(), DomainFile.DEFAULT_VERSION);
			fail();
		}
		catch (ClassCastException e) {
			// pass
		}
		waitForSwing();

		assertEquals(Set.of(this), Set.copyOf(program.getConsumerList()));
	}

	@Test
	public void testOpenTraces() throws Exception {
		createTrace();
		createProgram();
		waitForDomainObject(tb.trace);
		waitForDomainObject(program);

		Collection<Trace> result =
			traceManager.openTraces(Set.of(tb.trace.getDomainFile(), program.getDomainFile()));
		assertEquals(Set.of(tb.trace), Set.copyOf(result));

		assertEquals(Set.of(tb, traceManager), Set.copyOf(tb.trace.getConsumerList()));
		assertEquals(Set.of(this), Set.copyOf(program.getConsumerList()));
	}

	@Test
	public void testSaveTrace() throws Exception {
		createTrace();
		waitForDomainObject(tb.trace);

		assertFalse(tb.trace.getDomainFile().getPathname().contains("New Traces"));

		// Technically doesn't have to be open in the manager
		traceManager.saveTrace(tb.trace);
		waitForDomainObject(tb.trace);

		assertTrue(tb.trace.getDomainFile().getPathname().contains("New Traces"));
	}

	@Test
	public void testCloseTrace() throws Exception {
		createAndOpenTrace();
		waitForDomainObject(tb.trace);

		assertEquals(Set.of(tb, traceManager), Set.copyOf(tb.trace.getConsumerList()));

		traceManager.closeTrace(tb.trace);
		waitForSwing();

		assertEquals(Set.of(tb), Set.copyOf(tb.trace.getConsumerList()));
		assertEquals(Set.of(), traceManager.getOpenTraces());
	}

	@Test
	public void testFollowPresent() throws Throwable {
		createRmiConnection();
		createAndOpenTrace();
		TraceThread thread;
		try (Transaction tx = tb.startTransaction()) {
			tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION);
			thread = tb.createObjectsProcessAndThreads();
		}
		Target target = rmiCx.publishTarget(tool, tb.trace);
		traceManager.activateThread(thread);
		waitForSwing();

		assertEquals(ControlMode.RO_TARGET, editingService.getCurrentMode(tb.trace));
		long initSnap = target.getSnap();
		assertEquals(initSnap, traceManager.getCurrentSnap());

		rmiCx.setLastSnapshot(tb.trace, initSnap + 1);
		rmiCx.synthActivate(thread.getObject());
		waitForSwing();

		assertEquals(initSnap + 1, target.getSnap());
		assertEquals(initSnap + 1, traceManager.getCurrentSnap());

		editingService.setCurrentMode(tb.trace, ControlMode.RO_TRACE);

		rmiCx.setLastSnapshot(tb.trace, initSnap + 2);
		rmiCx.synthActivate(thread.getObject());
		waitForSwing();

		assertEquals(initSnap + 2, target.getSnap());
		assertEquals(initSnap + 1, traceManager.getCurrentSnap());

		editingService.setCurrentMode(tb.trace, ControlMode.RO_TARGET);
		waitForSwing();

		assertEquals(initSnap + 2, target.getSnap());
		assertEquals(initSnap + 2, traceManager.getCurrentSnap());

		rmiCx.setLastSnapshot(tb.trace, initSnap + 3);
		rmiCx.synthActivate(thread.getObject());
		waitForSwing();

		assertEquals(initSnap + 3, target.getSnap());
		assertEquals(initSnap + 3, traceManager.getCurrentSnap());
	}

	@Test
	public void testSynchronizeFocusTraceToModel() throws Throwable {
		createRmiConnection();
		addActivateMethods();
		createAndOpenTrace();
		TraceThread thread;
		try (Transaction tx = tb.startTransaction()) {
			tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION);
			thread = tb.createObjectsProcessAndThreads();
			tb.createObjectsFramesAndRegs(thread, Lifespan.nowOn(0), tb.host, 2);
		}
		rmiCx.publishTarget(tool, tb.trace);
		waitForSwing();

		assertTrue(activationMethodsQueuesEmpty());

		traceManager.activateTrace(tb.trace);
		waitForSwing();
		// TraceManager forces a default thread
		assertEquals(thread, traceManager.getCurrentThread());
		assertEquals(Map.ofEntries(
			Map.entry("thread", thread.getObject())),
			rmiMethodActivateThread.expect());
		assertTrue(activationMethodsQueuesEmpty());

		traceManager.activateThread(thread);
		waitForSwing();
		// Focus didn't change, so no activation.
		assertTrue(activationMethodsQueuesEmpty());

		TraceObject frame0 = tb.obj("Processes[1].Threads[1].Stack[0]");
		TraceObject frame1 = tb.obj("Processes[1].Threads[1].Stack[1]");

		// Starting with 0 results in no change in coordinates, so ignored
		traceManager.activateFrame(1);
		waitForSwing();
		assertEquals(Map.ofEntries(
			Map.entry("frame", frame1)),
			rmiMethodActivateFrame.expect());
		assertTrue(activationMethodsQueuesEmpty());

		traceManager.activateFrame(0);
		waitForSwing();
		assertEquals(Map.ofEntries(
			Map.entry("frame", frame0)),
			rmiMethodActivateFrame.expect());
		assertTrue(activationMethodsQueuesEmpty());
	}

	@Test
	public void testSynchronizeFocusModelToTrace() throws Throwable {
		createRmiConnection();
		addActivateMethods();
		createAndOpenTrace();
		TraceThread thread;
		try (Transaction tx = tb.startTransaction()) {
			tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION);
			thread = tb.createObjectsProcessAndThreads();
			tb.createObjectsFramesAndRegs(thread, Lifespan.nowOn(0), tb.host, 2);
		}
		rmiCx.publishTarget(tool, tb.trace);
		waitForSwing();

		// Focus should never be reflected back to target
		assertTrue(activationMethodsQueuesEmpty());

		assertNull(traceManager.getCurrentTrace());

		rmiCx.synthActivate(tb.obj("Processes[1]"));
		waitForSwing();

		// TraceManager forces a default thread
		assertEquals(tb.trace, traceManager.getCurrentTrace());
		assertEquals(thread, traceManager.getCurrentThread());

		rmiCx.synthActivate(thread.getObject());
		waitForSwing();
		assertEquals(thread, traceManager.getCurrentThread());

		TraceObject frame0 = tb.obj("Processes[1].Threads[1].Stack[0]");
		TraceObject frame1 = tb.obj("Processes[1].Threads[1].Stack[1]");

		// Starting with 0 results in no change in coordinates, so ignored
		rmiCx.synthActivate(frame1);
		waitForSwing();
		assertEquals(1, traceManager.getCurrentFrame());

		rmiCx.synthActivate(frame0);
		waitForSwing();
		assertEquals(0, traceManager.getCurrentFrame());

		// Focus should never be reflected back to target
		assertTrue(activationMethodsQueuesEmpty());
	}

	@Test
	public void testSynchronizeTimeTargetToGui() throws Throwable {
		createRmiConnection();
		addActivateWithTimeMethods();
		createAndOpenTrace();
		TraceThread thread;
		try (Transaction tx = tb.startTransaction()) {
			tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION);
			thread = tb.createObjectsProcessAndThreads();
			tb.createObjectsFramesAndRegs(thread, Lifespan.nowOn(0), tb.host, 2);
		}
		rmiCx.publishTarget(tool, tb.trace);
		waitForSwing();

		assertTrue(activationMethodsQueuesEmpty());
		assertNull(traceManager.getCurrentTrace());

		try (Transaction tx = tb.startTransaction()) {
			rmiCx.setLastSnapshot(tb.trace, Long.MIN_VALUE)
					.setSchedule(TraceSchedule.parse("0:10"));
		}
		rmiCx.synthActivate(tb.obj("Processes[1].Threads[1].Stack[0]"));
		waitForSwing();

		assertEquals(TraceSchedule.parse("0:10"), traceManager.getCurrent().getTime());
		assertTrue(activationMethodsQueuesEmpty());
	}

	@Test
	public void testTimeSupportNoTimeParam() throws Throwable {
		createRmiConnection();
		addActivateMethods();
		createAndOpenTrace();
		TraceThread thread;
		try (Transaction tx = tb.startTransaction()) {
			tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION);
			thread = tb.createObjectsProcessAndThreads();
		}
		Target target = rmiCx.publishTarget(tool, tb.trace);
		waitForSwing();

		assertNull(target.getSupportedTimeForm(thread.getObject(), 0));
	}

	@Test
	public void testTimeSupportSnapParam() throws Throwable {
		createRmiConnection();
		addActivateWithSnapMethods();
		createAndOpenTrace();
		TraceObject thread;
		TraceObject root;
		try (Transaction tx = tb.startTransaction()) {
			root = tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION).getChild();
			thread = tb.createObjectsProcessAndThreads().getObject();
		}
		Target target = rmiCx.publishTarget(tool, tb.trace);
		waitForSwing();

		assertNull(target.getSupportedTimeForm(thread, 0));

		try (Transaction tx = tb.startTransaction()) {
			root.setAttribute(Lifespan.nowOn(0), TraceEventScope.KEY_TIME_SUPPORT,
				ScheduleForm.SNAP_ONLY.name());
		}
		assertEquals(ScheduleForm.SNAP_ONLY, target.getSupportedTimeForm(thread, 0));

		try (Transaction tx = tb.startTransaction()) {
			root.setAttribute(Lifespan.nowOn(0), TraceEventScope.KEY_TIME_SUPPORT,
				ScheduleForm.SNAP_ANY_STEPS_OPS.name());
		}
		// Constrained by method parameter
		assertEquals(ScheduleForm.SNAP_ONLY, target.getSupportedTimeForm(thread, 0));
	}

	@Test
	public void testTimeSupportTimeParam() throws Throwable {
		createRmiConnection();
		addActivateWithTimeMethods();
		createAndOpenTrace();
		TraceObject thread;
		TraceObject root;
		try (Transaction tx = tb.startTransaction()) {
			root = tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION).getChild();
			thread = tb.createObjectsProcessAndThreads().getObject();
		}
		Target target = rmiCx.publishTarget(tool, tb.trace);
		waitForSwing();

		assertNull(target.getSupportedTimeForm(thread, 0));

		try (Transaction tx = tb.startTransaction()) {
			root.setAttribute(Lifespan.nowOn(0), TraceEventScope.KEY_TIME_SUPPORT,
				ScheduleForm.SNAP_ONLY.name());
		}
		assertEquals(ScheduleForm.SNAP_ONLY, target.getSupportedTimeForm(thread, 0));

		try (Transaction tx = tb.startTransaction()) {
			root.setAttribute(Lifespan.nowOn(0), TraceEventScope.KEY_TIME_SUPPORT,
				ScheduleForm.SNAP_EVT_STEPS.name());
		}
		assertEquals(ScheduleForm.SNAP_EVT_STEPS, target.getSupportedTimeForm(thread, 0));

		try (Transaction tx = tb.startTransaction()) {
			root.setAttribute(Lifespan.nowOn(0), TraceEventScope.KEY_TIME_SUPPORT,
				ScheduleForm.SNAP_ANY_STEPS.name());
		}
		assertEquals(ScheduleForm.SNAP_ANY_STEPS, target.getSupportedTimeForm(thread, 0));

		try (Transaction tx = tb.startTransaction()) {
			root.setAttribute(Lifespan.nowOn(0), TraceEventScope.KEY_TIME_SUPPORT,
				ScheduleForm.SNAP_ANY_STEPS_OPS.name());
		}
		assertEquals(ScheduleForm.SNAP_ANY_STEPS_OPS, target.getSupportedTimeForm(thread, 0));
	}

	@Test
	public void testSynchronizeTimeGuiToTargetFailsWhenNoTimeParam() throws Throwable {
		createRmiConnection();
		addActivateMethods();
		createAndOpenTrace();
		TraceThread thread;
		try (Transaction tx = tb.startTransaction()) {
			tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION);
			thread = tb.createObjectsProcessAndThreads();
			tb.trace.getTimeManager()
					.getSnapshot(0, true)
					.setEventThread(thread);
		}
		rmiCx.publishTarget(tool, tb.trace);
		waitForSwing();

		var activate1 = rmiMethodActivateThread.expect(args -> {
			assertEquals(Map.ofEntries(
				Map.entry("thread", thread.getObject())),
				args);
			return null;
		});
		traceManager.activate(DebuggerCoordinates.NOWHERE.thread(thread).snap(0));
		waitOn(activate1);

		var activate2 = rmiMethodActivateThread.expect(args -> {
			fail();
			return null;
		});
		traceManager.activateSnap(1);
		waitForSwing();
		assertThat(tool.getStatusInfo(), Matchers.containsString("Switch to Trace or Emulate"));
		assertFalse(activate2.isDone());
	}

	@Test
	public void testSynchronizeTimeGuiToTarget() throws Throwable {
		createRmiConnection();
		addActivateWithTimeMethods();
		createAndOpenTrace();
		TraceThread thread;
		TraceObject root;
		try (Transaction tx = tb.startTransaction()) {
			root = tb.trace.getObjectManager().createRootObject(SCHEMA_SESSION).getChild();
			thread = tb.createObjectsProcessAndThreads();
			root.setAttribute(Lifespan.nowOn(0), TraceEventScope.KEY_TIME_SUPPORT,
				ScheduleForm.SNAP_EVT_STEPS.name());
			tb.trace.getTimeManager()
					.getSnapshot(0, true)
					.setEventThread(thread);
		}
		rmiCx.publishTarget(tool, tb.trace);
		waitForSwing();

		var activate1 = rmiMethodActivateThread.expect(args -> {
			assertEquals(Map.ofEntries(
				Map.entry("thread", thread.getObject())),
				// time is optional and not changed, so omitted
				args);
			return null;
		});
		traceManager.activate(DebuggerCoordinates.NOWHERE.thread(thread).snap(0));
		waitOn(activate1);

		var activate2 = rmiMethodActivateThread.expect(args -> {
			assertEquals(Map.ofEntries(
				Map.entry("thread", thread.getObject()),
				Map.entry("time", "0:1")),
				args);
			return null;
		});
		traceManager.activateTime(TraceSchedule.snap(0).steppedForward(thread, 1));
		waitOn(activate2);
	}
}

/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.debug.tests.performance;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventFilter;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.tests.AbstractDebugPerformanceTest;
import org.eclipse.test.performance.Dimension;

/**
 * Tests performance of stepping.
 */
public class PerfSteppingTests extends AbstractDebugPerformanceTest {
	
	public PerfSteppingTests(String name) {
		super(name);
	}
	
	class MyFilter implements IDebugEventFilter {
		
		private IJavaThread fThread = null;
		private Object fLock;
		private DebugEvent[] EMPTY = new DebugEvent[0];

		public MyFilter(IJavaThread thread, Object lock) {
			fThread = thread;
			fLock = lock;
		}
		
		public DebugEvent[] filterDebugEvents(DebugEvent[] events) {
			for (int i = 0; i < events.length; i++) {
				DebugEvent event = events[i];
				if (event.getSource() == fThread) {
					if (event.getKind() == DebugEvent.SUSPEND && event.getDetail() == DebugEvent.STEP_END) {
						synchronized (fLock) {
							fLock.notifyAll();
						}
					}
					return EMPTY;
				}
			}
			return events;
		}
		
		public void step() {
			synchronized (fLock) {
				try {
					fThread.stepOver();
				} catch (DebugException e) {
					assertTrue(e.getMessage(), false);
				}
				try {
					fLock.wait();
				} catch (InterruptedException e) {
					assertTrue(e.getMessage(), false);
				}
			}
			 
		}
		
	}

	/**
	 * Tests stepping over without taking into account event processing in the UI.
	 * 
	 * @throws Exception
	 */
	public void testBareStepOver() throws Exception {
		tagAsSummary("Rapid Stepping", Dimension.CPU_TIME);
		String typeName = "PerfLoop";
		IJavaLineBreakpoint bp = createLineBreakpoint(20, typeName);
		
		IJavaThread thread= null;
		try {			
			thread= launchToBreakpoint(typeName, false);
			
			// warm up
			Object lock = new Object();
			MyFilter filter = new MyFilter(thread, lock);
			DebugPlugin.getDefault().addDebugEventFilter(filter);
			IJavaStackFrame frame = (IJavaStackFrame)thread.getTopStackFrame();
			for (int n= 0; n < 10; n++) {
				for (int i = 0; i < 100; i++) {
					filter.step();
				}
			}			
			DebugPlugin.getDefault().removeDebugEventFilter(filter);			
			
			// real test
			lock = new Object();
			filter = new MyFilter(thread, lock);
			DebugPlugin.getDefault().addDebugEventFilter(filter);
			
			frame = (IJavaStackFrame)thread.getTopStackFrame();
			for (int n= 0; n < 10; n++) {
				startMeasuring();
				for (int i = 0; i < 100; i++) {
					filter.step();
				}
				stopMeasuring();
			}
			commitMeasurements();
			assertPerformance();
			
			DebugPlugin.getDefault().removeDebugEventFilter(filter);
		} finally {
			terminateAndRemove(thread);
			removeAllBreakpoints();
		}		
	}
}

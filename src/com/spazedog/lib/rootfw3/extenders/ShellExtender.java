/*
 * This file is part of the RootFW Project: https://github.com/spazedog/rootfw
 *  
 * Copyright (c) 2013 Daniel Bergløv
 *
 * RootFW is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * RootFW is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public License
 * along with RootFW. If not, see <http://www.gnu.org/licenses/>
 */

package com.spazedog.lib.rootfw3.extenders;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.spazedog.lib.rootfw3.RootFW;
import com.spazedog.lib.rootfw3.RootFW.ExtenderGroupTransfer;
import com.spazedog.lib.rootfw3.containers.Data;
import com.spazedog.lib.rootfw3.interfaces.ExtenderGroup;

public class ShellExtender {
	public final static String TAG = RootFW.TAG + ".ShellExtender";
	
	private final static Pattern mPatternBinarySearch = Pattern.compile("%binary([ ]*)");
	
	/**
	 * This class is used to communicate with the shell. 
	 * <br />
	 * Note that this implements the {@link ExtenderGroup} interface, which means that it does not allow anything outside {@link RootFW} to create an instance of it. Use {@link RootFW#shell()} to retrieve an instance. 
	 */
	public static class Shell implements ExtenderGroup {
		protected BufferedReader mInputStream;
		protected DataOutputStream mOutputStream;
		
		protected Integer[] mResultCodes = new Integer[]{0};
		protected List<String[]> mCommands = new ArrayList<String[]>();
		
		/**
		 * This is used internally by {@link RootFW} to get a new instance of this class. 
		 */
		public static ExtenderGroupTransfer getInstance(RootFW parent, ExtenderGroupTransfer transfer) {
			return transfer.setInstance((ExtenderGroup) new Shell((BufferedReader) transfer.arguments[0], (DataOutputStream) transfer.arguments[1]));
		}
		
		/**
		 * Create a new instance of this class.
		 * 
		 * @param input
		 *     The InputStream from the RootFW connection
		 *     
		 * @param output
		 *     The OutputStream from the RootFW connection
		 */
		private Shell(BufferedReader inputstream, DataOutputStream outputStream) {
			mInputStream = inputstream;
			mOutputStream = outputStream;
		}
		
		/**
		 * This is the same as addCommands(), only this one will auto create multiple attempts for each command using each defined binary in RootFW.Config.BINARY. You can use the prefix %binary to represent where the binary should be injected. 
		 * <br />
		 * For an example, if you add the command <code>buildCommands('%binary df -h')</code>, this method will create <code>new String[]{'busybox df -h', 'toolbox df -h', 'df -h'}</code>. This makes it easier to add multiple attempts without having to type in each attempt via addAttempts().
		 * 
		 * @param commands
		 *     A string containing a command with %binary prefix
		 *     
		 * @return
		 *     This instance
		 */
		public ShellExtender.Shell buildCommands(String... commands) {
			for (int i=0; i < commands.length; i++) {
				String[] cmd = new String[ RootFW.Config.BINARIES.size() + 1 ];
				
				for (int x=0; x < RootFW.Config.BINARIES.size(); x++) {
					cmd[x] = mPatternBinarySearch.matcher(commands[i]).replaceAll( RootFW.Config.BINARIES.get(x) + " " ) + " 2>/dev/null";
				}
				
				cmd[ RootFW.Config.BINARIES.size() ] = mPatternBinarySearch.matcher(commands[i]).replaceAll("") + " 2>/dev/null";
				
				mCommands.add(cmd);
			}
			
			return this;
		}
		
		/**
		 * This is the same as buildCommands(), only this one just produces one command with generated attempts per argument.
		 * <br />
		 * If you add the command <code>buildAttempts('%binary df -h', '%binary df')</code>, this method will create one command with the attempts <code>new String[]{'busybox df -h', 'toolbox df -h', 'df -h', 'busybox df', 'toolbox df', 'df'}</code>.
		 * 
		 * @param commands
		 *     A string containing a command with %binary prefix
		 *     
		 * @return
		 *     This instance
		 */
		public ShellExtender.Shell buildAttempts(String... commands) {
			List<String> cmd = new ArrayList<String>();
			
			for (int i=0; i < commands.length; i++) {
				for (int x=0; x < RootFW.Config.BINARIES.size(); x++) {
					cmd.add( mPatternBinarySearch.matcher(commands[i]).replaceAll( RootFW.Config.BINARIES.get(x) + " " ) + " 2>/dev/null" );
				}
				
				cmd.add( mPatternBinarySearch.matcher(commands[i]).replaceAll("") + " 2>/dev/null" );
			}
			
			mCommands.add( cmd.toArray( new String[ cmd.size() ] ) );
			
			return this;
		}
		
		/**
		 * This is used to add multiple commands to be executed in the shell. Each argument should be a separate command to be executed. Each command output will be merged in the ShellResult.
		 * <br />
		 * If one command fails (Does not return a valid result code), it will stop executing the rest of the commands. 
		 * 
		 * @param commands
		 *     A string containing a command
		 *     
		 * @return
		 *     This instance
		 */
		public ShellExtender.Shell addCommands(String... commands) {
			for (int i=0; i < commands.length; i++) {
				mCommands.add( new String[]{ commands[i] } );
			}
			
			return this;
		}
		
		/**
		 * This is used to add multiple attempt for one command. The different between this and buildAttempts(), is that this one does not auto generate more attempts based on defined binaries.
		 * 
		 * @param commands
		 *     A string containing an attempts
		 *     
		 * @return
		 *     This instance
		 */
		public ShellExtender.Shell addAttempts(String... commands) {
			mCommands.add( commands );
			
			return this;
		}
		
		/**
		 * Whether or not a command was successful or not, depends on the result code returned by the shell. This result code determines the result of <code>ShellResult.wasSuccessfull()</code> and it also controls when to stop executing multiple command attempts.
		 * <br />
		 * By default, 0 is considered successful, but you might need other result codes to be considered successful as well. This method allows you to add more result codes to be considered successful.
		 * 
		 * @param result
		 *     A result code to be considered successful
		 *     
		 * @return
		 *     This instance
		 */
		public ShellExtender.Shell setResultCodes(Integer... result) {
			mResultCodes = result;
			
			return this;
		}
		
		/**
		 * Whenever you add commands using <code>builtCommands()</code> and <code>addCommands()</code>, or adds more result codes via <code>setResultCodes()</code>, this is all cached until you execute <code>run()</code>. 
		 * <br >
		 * This method will reset this cache without having to execute <code>run()</code>.
		 *     
		 * @return
		 *     This instance
		 */
		public ShellExtender.Shell reset() {
			mCommands.clear();
			mResultCodes = new Integer[]{0};
			
			return this;
		}
		
		/**
		 * This method will execute all of the commands that has been added via <code>addCommands()</code> and <code>builtCommands()</code>.
		 *     
		 * @return
		 *     a new instance of ShellResult with all of the shell output and information like the result code. 
		 */
		public ShellResult run() {
			synchronized(mOutputStream) {
				List<String> output = new ArrayList<String>();
				List<Integer> cmdNumber = new ArrayList<Integer>();
				Integer resultCode = -1;
				Integer[] codes = mResultCodes;
				
				RootFW.log(TAG + "::run()", "Preparing to execute " + mCommands.size() + " command(s)");
				
				try {			
					commandLoop:
					for (int i=0; i < mCommands.size(); i++) {
						String[] commandTries = mCommands.get(i);
						
						RootFW.log(TAG + "::run()", "Executing command number " + (i+1) + " containing " + commandTries.length + " attempt(s)");
						
						for (int x=0; x < commandTries.length; x++) {
							RootFW.log(TAG + "::run()", "Running attempt number " + (x+1) + " [" + commandTries[x] + "]");
							
							/*
							 * If we try to execute a command like 'cat file', and this file does not contain any line breaks, 
							 * then the file output and our token 'EOL:a00c38d8:EOL' will be merged in the same line. 
							 * On some shells we can handle this by adding a double line break after our command, but other shells seams to trim this away. 
							 * So in order to make support for various shells and output, to make sure that the output is not merged with our token, we add 
							 * an additional empty echo after the command. This will force an empty line between the two.
							 */
							String command = commandTries[x] + "\n";
							command += "status=$? && echo ''\n";
							command += "echo EOL:a00c38d8:EOL\n";
							command += "echo $status\n";
							command += "echo EOL:a00c38d8:EOL\n";
							
							mOutputStream.write( command.getBytes() );
							mOutputStream.flush();
							
							String input;
							List<String> lines = new ArrayList<String>();
							
							try {
								while ((input = mInputStream.readLine()) != null) {
									if (!input.contains("EOL:a00c38d8:EOL")) {
										lines.add(input);
										
									} else {
										resultCode = -1;
												
										/* It is important that readLine() get's to be executed until it reaches 'EOL:a00c38d8:EOL'. 
										 * Otherwise, the output will not be cleaned out, and will be added to the next command executed. 
										 */
										while ((input = mInputStream.readLine()) != null && !input.contains("EOL:a00c38d8:EOL")) {
											if (input.length() > 0) {
												try { 
													resultCode = Integer.parseInt( input );
													
												} catch(Throwable e) { continue; }
											}
										}
										
										break;
									}
								}
								
								for (int y=0; y < mResultCodes.length; y++) {
									if ((int) resultCode == (int) mResultCodes[y]) {
										if (mCommands.size() == 1) {
											output = lines;
											
										} else {
											output.addAll(lines);
										}
										
										cmdNumber.add(x);
										
										RootFW.log(TAG + "::run()", "The attempt number " + (x+1) + " was successfully executed and returned result code (" + resultCode + ")");
										
										continue commandLoop;
										
									} else if (y == mResultCodes.length-1 && x == commandTries.length-1) {
										RootFW.log(TAG + "::run()", "The command number " + (i+1) + " failed. Ending shell execution"); break commandLoop;
									}
								}
								
								RootFW.log(TAG + "::run()", "The attempt number " + (x+1) + " failed. Continuing to the next attempt");
								
							} catch (Throwable e) {}
						}
					}
				
				} catch (Throwable e) {}
				
				reset();
				
				// Remove leading line breaks
				while (output.size() > 0) {
					Integer last = output.size()-1;
					
					if (output.get(0).length() == 0 || output.get(last).length() == 0) {
						if (output.get(last).length() == 0)
							output.remove(last);
						
						if (output.size() > 0 && output.get(0).length() == 0) 
							output.remove(0);
					}
					
					break;
				}
				
				return new ShellResult(output.toArray(new String[output.size()]), resultCode, codes, cmdNumber.toArray(new Integer[cmdNumber.size()]));
			}
		}
		
		/**
		 * This method allows you to directly execute one single command without having to use <code>addCommands()</code> and <code>builtCommands()</code>. 
		 * <br />
		 * Note that if you have already used <code>addCommands()</code> or <code>builtCommands()</code>, this argument will just be added to the stack. It does not reset anything before executing.
		 * 
		 * @param command
		 *     One single command to be executed in the shell
		 *     
		 * @return
		 *     a new instance of ShellResult with all of the shell output and information like the result code. 
		 */
		public ShellResult run(String command) {
			return addCommands( command ).run();
		}
	}

	/**
	 * This class get's returned after running shell commands. It contains all of the information returned by the shell after executing the stack of commands. 
	 * <br />
	 * Note that this class is extended from the Data class, so it will contain all of the data tools available in that class as well.
	 */
	public static class ShellResult extends Data<ShellResult> {
		private Integer mResultCode;
		private Integer[] mValidResults;
		private Integer[] mCommandNumber;
		
		public ShellResult(String[] lines, Integer result, Integer[] validResults, Integer[] commandNumber) {
			super(lines);
			
			mResultCode = result;
			mValidResults = validResults;
			mCommandNumber = commandNumber;
		}
		
		/**
		 * @return
		 *     The result code returned by the shell
		 */
		public Integer getResultCode() {
			return mResultCode;
		}
		
		/**
		 * This method will compare the result code returned by the shell, with the stack of result codes added via <code>ShellExtender.setResultCodes()</code>.
		 *     
		 * @return
		 *     <code>True</code> if the result code was found in the stack
		 */
		public Boolean wasSuccessful() {
			for (int i=0; i < mValidResults.length; i++) {
				if ((int) mValidResults[i] == (int) mResultCode) {
					return true;
				}
			}
			
			return false;
		}
		
		/**
		 * This method will return the number of each successful command attempts. Since each command can be multiple attempts, this can be used to determine which of the attempts was successful. 
		 * 
		 * @param cmdNum
		 *     The number of the command to check (Not the attempt number)
		 *     
		 * @return
		 *     The number of the successful attempt for the defined command
		 */
		public Integer getCommandNumber(Integer cmdNum) {
			return cmdNum > mCommandNumber.length ? -1 : mCommandNumber[cmdNum];
		}
	}
}

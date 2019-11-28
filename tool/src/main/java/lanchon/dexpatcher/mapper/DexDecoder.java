/*
 * DexPatcher - Copyright 2015-2019 Rodrigo Balerdi
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package lanchon.dexpatcher.mapper;

import java.util.HashSet;

import lanchon.dexpatcher.core.logger.Logger;
import lanchon.dexpatcher.core.util.Label;
import lanchon.dexpatcher.mapper.DexDecoderModule.ItemType;

import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.rewriter.DexRewriter;

import static lanchon.dexpatcher.core.logger.Logger.Level.*;

public abstract class DexDecoder {

	protected static class ItemRewriter implements NameDecoder.ErrorHandler {

		private static final boolean LOG_DECODED_TYPES = false;

		protected final NameDecoder nameDecoder;
		protected final Logger logger;
		protected final String logPrefix;
		protected final Logger.Level infoLevel;
		protected final Logger.Level errorLevel;

		private final HashSet<String> loggedMessages = new HashSet<>();

		protected String definingClass;
		protected ItemType itemType;
		protected String value;

		public ItemRewriter(NameDecoder nameDecoder, Logger logger, String logPrefix, Logger.Level infoLevel,
				Logger.Level errorLevel) {
			this.nameDecoder = nameDecoder;
			this.logger = logger;
			this.logPrefix = logPrefix;
			this.infoLevel = infoLevel;
			this.errorLevel = errorLevel;
		}

		public String rewriteItem(String definingClass, ItemType itemType, String value) {
			this.definingClass = definingClass;
			this.itemType = itemType;
			this.value = value;
			String decodedValue = nameDecoder.decode(value, this);
			if (decodedValue != value && decodedValue != null) {
				// NOTE: This call to logger.isLogging() is not synchronized.
				if (infoLevel != NONE && logger.isLogging(infoLevel) && !decodedValue.equals(value)) {
					StringBuilder sb = buildMessage();
					sb.append("decoded to '").append(formatValue(decodedValue)).append("'");
					log(infoLevel, sb.toString());
				}
			}
			return decodedValue;
		}

		@Override
		public void onError(String message, String string, int codeFrom, int codeTo, int errorFrom, int errorTo) {
			// NOTE: This call to logger.isLogging() is not synchronized.
			if (errorLevel != NONE && logger.isLogging(errorLevel)) {
				StringBuilder sb = buildMessage();
				sb.append(message);
				sb.append(" in '").append(string, codeFrom, errorFrom)
						.append("[->]").append(string, errorFrom, errorTo).append("[<-]")
						.append(string, errorTo, codeTo).append("'");
				log(errorLevel, sb.toString());
			}
		}

		protected StringBuilder buildMessage() {
			StringBuilder sb = new StringBuilder();
			if (logPrefix != null) sb.append(logPrefix).append(": ");
			if (definingClass != null) {
				sb.append("type '").append(Label.fromClassDescriptor(definingClass));
				if (LOG_DECODED_TYPES) {
					String decodedDefiningClass = nameDecoder.decode(definingClass);
					if (!definingClass.equals(decodedDefiningClass)) {
						sb.append("' -> '").append(Label.fromClassDescriptor(decodedDefiningClass));
					}
				}
				sb.append("': ");
			}
			sb.append(itemType.label).append(" '").append(formatValue(value)).append("': ");
			return sb;
		}

		protected String formatValue(String value) {
			return itemType == ItemType.NAKED_TYPE_NAME ? value.replace('/', '.') : value;
		}

		protected synchronized void log(Logger.Level level, String message) {
			if (logger.isLogging(level) && loggedMessages.add(message)) logger.log(level, message);
		}

	}

	public static DexFile decode(DexFile dex, NameDecoder nameDecoder, Logger logger, String logPrefix,
			Logger.Level infoLevel, Logger.Level errorLevel) {
		return decode(dex, new ItemRewriter(nameDecoder, logger, logPrefix, infoLevel, errorLevel));
	}

	protected static DexFile decode(DexFile dex, final ItemRewriter itemRewriter) {
		DexDecoderModule module = new DexDecoderModule() {
			@Override
			public String rewriteItem(String definingClass, ItemType type, String value) {
				return itemRewriter.rewriteItem(definingClass, type, value);
			}
		};
		return new DexRewriter(module).rewriteDexFile(dex);
	}

}
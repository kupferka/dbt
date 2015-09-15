/*
 * $Id$ 
 * $Revision$ $Date$
 *
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, in a file called gpl.txt or license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 */
package org.urmel.dbt.rc.servlets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdfwriter.COSWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.BadSecurityHandlerException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.XMLOutputter;
import org.mycore.access.MCRAccessManager;
import org.mycore.common.content.MCRContent;
import org.mycore.common.events.MCREvent;
import org.mycore.common.events.MCREventManager;
import org.mycore.frontend.MCRFrontendUtil;
import org.mycore.frontend.servlets.MCRServlet;
import org.mycore.frontend.servlets.MCRServletJob;
import org.urmel.dbt.opc.datamodel.Catalog;
import org.urmel.dbt.opc.datamodel.Catalogues;
import org.urmel.dbt.rc.datamodel.slot.Slot;
import org.urmel.dbt.rc.datamodel.slot.SlotEntry;
import org.urmel.dbt.rc.datamodel.slot.entries.FileEntry;
import org.urmel.dbt.rc.datamodel.slot.entries.OPCRecordEntry;
import org.urmel.dbt.rc.persistency.FileEntryManager;
import org.urmel.dbt.rc.persistency.SlotManager;
import org.urmel.dbt.rc.utils.SlotEntryTransformer;

/**
 * @author Ren\u00E9 Adler (eagle)
 *
 */
@MultipartConfig
public class SlotServlet extends MCRServlet {

    private static final long serialVersionUID = -3138681111200495882L;

    private static final Logger LOGGER = LogManager.getLogger(SlotServlet.class);

    // Error code: for a empty file or empty file parameter
    private static final int ERROR_EMPTY_FILE = 100;

    // Error code: for a PDF document with exceeded page limit
    private static final int ERROR_PAGE_LIMIT_EXCEEDED = 101;

    // Error code: for a unsupported PDF document
    private static final int ERROR_NOT_SUPPORTED = 102;

    private static final SlotManager SLOT_MGR = SlotManager.instance();

    @SuppressWarnings("unchecked")
    public void doGetPost(final MCRServletJob job) throws Exception {
        final HttpServletRequest req = job.getRequest();
        final HttpServletResponse res = job.getResponse();

        // checks path and return the file content.
        final String path = req.getPathInfo();

        if (path != null) {
            final StringTokenizer st = new StringTokenizer(path, "/");

            final String slotId = st.nextToken();
            final String entryId = st.nextToken();
            final String fileName = st.nextToken();

            if (slotId != null && entryId != null && fileName != null) {
                final Slot slot = SLOT_MGR.getSlotById(slotId);

                if (!SlotManager.checkPermission(slot.getMCRObjectID(), MCRAccessManager.PERMISSION_READ)
                        || !SlotManager.checkPermission(slot.getMCRObjectID(), MCRAccessManager.PERMISSION_WRITE)) {
                    res.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                final SlotEntry<FileEntry> slotEntry = (SlotEntry<FileEntry>) slot.getEntryById(entryId);

                if (slotEntry != null) {
                    final FileEntry fileEntry = (FileEntry) slotEntry.getEntry();
                    if (fileEntry != null && fileName.equals(fileEntry.getName())) {
                        MCRContent content = FileEntryManager.retrieve(slot, slotEntry);
                        content.sendTo(res.getOutputStream());
                        return;
                    }
                }
            }

            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // edit slot entries
        final String action = getParameter(req, "action");
        final String entry = getParameter(req, "entry");
        final String slotId = getParameter(req, "slotId");
        final String afterId = getParameter(req, "afterId");

        Element xml = null;
        final Document doc = (Document) (req.getAttribute("MCRXEditorSubmission"));
        if (doc != null) {
            xml = doc.getRootElement();
            LOGGER.debug(new XMLOutputter().outputString(xml));
        }

        if (slotId != null) {
            final Slot slot = SLOT_MGR.getSlotById(slotId);

            if (!SlotManager.checkPermission(slot.getMCRObjectID(), MCRAccessManager.PERMISSION_WRITE)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            final Element firstChild = xml != null && xml.getChildren().size() > 0 ? xml.getChildren().get(0) : null;

            if (firstChild != null && "search".equals(firstChild.getName())) {
                final String catalogId = req.getParameter("catalogId");
                final Catalog catalog = Catalogues.instance().getCatalogById(catalogId);

                final Map<String, String> params = new HashMap<String, String>();
                params.put("slotId", slotId);
                params.put("afterId", afterId);

                res.sendRedirect(MCRFrontendUtil.getBaseURL() + "opc/"
                        + (catalog != null && catalog.getISIL() != null && catalog.getISIL().size() > 0
                                ? catalog.getISIL().get(0) : catalogId)
                        + "/search/" + firstChild.getTextTrim() + toQueryString(params, true));
            } else {
                SlotEntry<?> slotEntry = xml != null ? SlotEntryTransformer.buildSlotEntry(xml) : null;

                boolean success = true;

                if (slotEntry == null && "upload".equals(action)) {
                    if (getParameter(req, "cancel") != null) {
                        res.sendRedirect(MCRFrontendUtil.getBaseURL() + "rc/" + slot.getSlotId() + "?XSL.Mode=edit");
                        return;
                    }

                    final Map<String, String> params = new HashMap<String, String>();
                    params.put("entry", entry);
                    params.put("slotId", slotId);
                    params.put("afterId", afterId);
                    params.put("invalid", "true");

                    final Part filePart = req.getPart("file");
                    final String fileName = getFilename(filePart);
                    final boolean isCopyrighted = Boolean.parseBoolean(getParameter(req, "copyrighted"));

                    if (fileName == null || fileName.length() == 0) {
                        params.put("errorcode", Integer.toString(ERROR_EMPTY_FILE));

                        res.sendRedirect(MCRFrontendUtil.getBaseURL() + "content/rc/entry-file.xml"
                                + toQueryString(params, false));
                        return;
                    }

                    slotEntry = new SlotEntry<FileEntry>();

                    final FileEntry fe = new FileEntry();
                    fe.setName(fileName);
                    fe.setCopyrighted(isCopyrighted);

                    if (isCopyrighted && "application/pdf".equals(filePart.getContentType())) {
                        ByteArrayOutputStream pdfCopy = null;
                        ByteArrayOutputStream pdfEncrypted = null;

                        try {
                            final int numPages = getNumPagesFromPDF(filePart.getInputStream());

                            LOGGER.info("Check num pages for \"" + fileName + "\": " + numPages);
                            if (numPages == -1 || numPages > 50) {
                                params.put("errorcode", Integer.toString(ERROR_PAGE_LIMIT_EXCEEDED));

                                res.sendRedirect(MCRFrontendUtil.getBaseURL() + "content/rc/entry-file.xml"
                                        + toQueryString(params, false));
                                return;
                            }

                            LOGGER.info("Make an supported copy for \"" + fileName + "\".");
                            pdfCopy = new ByteArrayOutputStream();
                            copyPDF(filePart.getInputStream(), pdfCopy);

                            LOGGER.info("Encrypt \"" + fileName + "\".");
                            pdfEncrypted = new ByteArrayOutputStream();
                            encryptPDF(slotEntry.getId(), new ByteArrayInputStream(pdfCopy.toByteArray()),
                                    pdfEncrypted);

                            fe.setContent(pdfEncrypted.toByteArray());
                        } catch (Exception e) {
                            LOGGER.error(e);
                            params.put("errorcode", Integer.toString(ERROR_NOT_SUPPORTED));

                            res.sendRedirect(MCRFrontendUtil.getBaseURL() + "content/rc/entry-file.xml"
                                    + toQueryString(params, false));
                            return;
                        } finally {
                            if (pdfCopy != null) {
                                pdfCopy.close();
                            }
                            if (pdfEncrypted != null) {
                                pdfEncrypted.close();
                            }
                        }
                    } else
                        fe.setContent(filePart.getInputStream());
                    fe.setComment(getParameter(req, "comment"));

                    ((SlotEntry<FileEntry>) slotEntry).setEntry(fe);
                }

                MCREvent evt = null;

                if ("order".equals(action)) {
                    final String items = getParameter(req, "items");
                    final StringTokenizer st = new StringTokenizer(items, ",");

                    final List<SlotEntry<?>> sortedEntries = new ArrayList<SlotEntry<?>>();
                    while (st.hasMoreTokens()) {
                        final String id = st.nextToken();
                        sortedEntries.add(slot.getEntryById(id));
                    }
                    slot.setEntries(sortedEntries);

                    SLOT_MGR.saveOrUpdate(slot);

                    res.sendError(HttpServletResponse.SC_OK);
                    return;
                } else if ("delete".equals(action)) {
                    final SlotEntry<?> se = slot.getEntryById(slotEntry.getId());
                    if (se != null) {
                        if (se.getEntry() instanceof OPCRecordEntry && !SlotManager.hasAdminPermission()
                                && ((OPCRecordEntry) se.getEntry()).getEPN() != null) {
                            LOGGER.debug("Set deletion mark: " + se);
                            ((OPCRecordEntry) se.getEntry()).setDeletionMark(true);
                            slot.setEntry(se);
                        } else {
                            LOGGER.debug("Remove entry: " + se);
                            success = slot.removeEntry(se);
                        }

                        evt = new MCREvent(SlotManager.ENTRY_TYPE, MCREvent.DELETE_EVENT);
                        evt.put(SlotManager.ENTRY_TYPE, se);
                    }
                } else if (slot.getEntries() == null) {
                    LOGGER.debug("Add new entry: " + slotEntry);
                    success = slot.addEntry(slotEntry);

                    evt = new MCREvent(SlotManager.ENTRY_TYPE, MCREvent.CREATE_EVENT);
                    evt.put(SlotManager.ENTRY_TYPE, slotEntry);
                } else {
                    final SlotEntry<?> se = slot.getEntryById(slotEntry.getId());
                    if (se != null) {
                        LOGGER.debug("Update entry: " + slotEntry);
                        slot.setEntry(slotEntry);

                        evt = new MCREvent(SlotManager.ENTRY_TYPE, MCREvent.UPDATE_EVENT);
                        evt.put(SlotManager.ENTRY_TYPE, slotEntry);
                    } else {
                        LOGGER.debug("Add new entry after \"" + afterId + "\".");
                        success = slot.addEntry(slotEntry, afterId);

                        evt = new MCREvent(SlotManager.ENTRY_TYPE, MCREvent.CREATE_EVENT);
                        evt.put(SlotManager.ENTRY_TYPE, slotEntry);
                    }
                }

                if (success) {
                    // put svn revision on event (needed on deletion) 
                    if (evt != null) {
                        evt.put("revision", Long.toString(SLOT_MGR.getLastRevision(slot)));
                    }

                    SLOT_MGR.saveOrUpdate(slot);

                    if (evt != null) {
                        if (evt.getObjectType().equals(SlotManager.ENTRY_TYPE)) {
                            evt.put("slotId", slot.getSlotId());
                        }

                        if (MCREvent.DELETE_EVENT.equals(evt.getEventType())) {
                            MCREventManager.instance().handleEvent(evt, MCREventManager.BACKWARD);
                        } else {
                            MCREventManager.instance().handleEvent(evt);
                        }
                    }
                }

                res.sendRedirect(MCRFrontendUtil.getBaseURL() + "rc/" + slot.getSlotId() + "?XSL.Mode=edit#"
                        + slotEntry.getId());
            }
        }
    }

    private static String toQueryString(final Map<String, String> parameters, final boolean withXSLPrefix) {
        StringBuffer queryStr = new StringBuffer();
        for (String name : parameters.keySet()) {
            if (parameters.get(name) != null) {
                if (queryStr.length() > 0) {
                    queryStr.append("&");
                }
                queryStr.append((withXSLPrefix ? "XSL." : "") + name + "=" + parameters.get(name));
            }
        }
        return queryStr.toString().length() > 0 ? "?" + queryStr.toString() : queryStr.toString();
    }

    private static String getFilename(final Part part) {
        for (String cd : part.getHeader("content-disposition").split(";")) {
            if (cd.trim().startsWith("filename")) {
                final String filename = cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
                return filename.substring(filename.lastIndexOf('/') + 1).substring(filename.lastIndexOf('\\') + 1); // MSIE fix.
            }
        }
        return null;
    }

    private static String getParameter(final HttpServletRequest req, final String name) {
        if (req.getContentType() != null && req.getContentType().toLowerCase().indexOf("multipart/form-data") > -1) {
            try {
                Part part = req.getPart(name);

                if (part == null)
                    return null;

                InputStream is = part.getInputStream();
                try (java.util.Scanner s = new java.util.Scanner(is)) {
                    return s.useDelimiter("\\A").hasNext() ? s.next() : "";
                } finally {
                    is.close();
                }
            } catch (IOException | ServletException e) {
                return null;
            }
        }

        return req.getParameter(name);
    }

    /**
     * Returns the number of pages from given PDF {@link InputStream}.
     * 
     * @param pdfInput the {@link InputStream}
     * @return the number of pages
     * @throws IOException
     */
    private static int getNumPagesFromPDF(final InputStream pdfInput) throws IOException {
        PDDocument doc = PDDocument.load(pdfInput);
        return doc.getNumberOfPages();
    }

    /**
     * Makes an save copy of given PDF {@link InputStream} to an new {@link OutputStram}.
     * 
     * @param pdfInput the PDF {@link InputStream}
     * @param pdfOutput the PDF {@link OutputStram}
     * @throws IOException
     * @throws COSVisitorException
     */
    private static void copyPDF(final InputStream pdfInput, final OutputStream pdfOutput)
            throws IOException, COSVisitorException {
        COSWriter writer = null;
        try {
            PDFParser parser = new PDFParser(pdfInput);
            parser.parse();

            COSDocument doc = parser.getDocument();

            writer = new COSWriter(pdfOutput);

            writer.write(doc);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    /**
     * Secures the PDF document and set the password.
     * 
     * @param password the password
     * @param pdfInput the PDF {@link InputStream}
     * @param pdfOutput the PDF {@link OutputStram}
     * @throws IOException
     * @throws BadSecurityHandlerException
     * @throws COSVisitorException
     */
    private static void encryptPDF(final String password, final InputStream pdfInput, final OutputStream pdfOutput)
            throws IOException, BadSecurityHandlerException, COSVisitorException {
        PDDocument doc = PDDocument.load(pdfInput);

        AccessPermission ap = new AccessPermission();

        ap.setCanAssembleDocument(false);
        ap.setCanExtractContent(false);
        ap.setCanExtractForAccessibility(false);
        ap.setCanFillInForm(false);
        ap.setCanModify(false);
        ap.setCanModifyAnnotations(false);
        ap.setCanPrint(false);
        ap.setCanPrintDegraded(false);
        ap.setReadOnly();

        if (!doc.isEncrypted()) {
            StandardProtectionPolicy spp = new StandardProtectionPolicy(password, null, ap);
            doc.protect(spp);

            doc.save(pdfOutput);
        }
    }
}

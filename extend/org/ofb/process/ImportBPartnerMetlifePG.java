/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 * Contributor: Carlos Ruiz - globalqss                                       *
 *****************************************************************************/
package org.ofb.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;

import org.adempiere.exceptions.DBException;
import org.adempiere.model.ImportValidator;
import org.adempiere.process.ImportProcess;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MContactInterest;
import org.compiere.model.MLocation;
import org.compiere.model.MUser;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.X_I_BPartner;
import org.compiere.model.X_R_ContactInterest;
import org.compiere.model.X_R_InterestArea;
import org.compiere.model.X_R_InterestAreaValues;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;

/**
 *	Import BPartners from I_BPartner metlife
 *
 * 	@author 	Italo Ni�oles
 * 	*/
public class ImportBPartnerMetlifePG extends SvrProcess 
implements ImportProcess
{
	/**	Client to be imported to		*/
	private int				m_AD_Client_ID = 0;
	/**	Delete old Imported				*/
	private boolean			m_deleteOldImported = false;
	/**	Only validate, don't import		*/
	private boolean			p_IsValidateOnly = false;

	/** Effective						*/
	private Timestamp		m_DateValue = null;

	/**
	 *  Prepare - e.g., get Parameters.
	 */
	protected void prepare()
	{
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (name.equals("AD_Client_ID"))
				m_AD_Client_ID = ((BigDecimal)para[i].getParameter()).intValue();
			else if (name.equals("DeleteOldImported"))
				m_deleteOldImported = "Y".equals(para[i].getParameter());
			else if (name.equals("IsValidateOnly"))
				p_IsValidateOnly = para[i].getParameterAsBoolean();
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
		if (m_DateValue == null)
			m_DateValue = new Timestamp (System.currentTimeMillis());
	}	//	prepare


	/**
	 *  Perform process.
	 *  @return Message
	 *  @throws Exception
	 */
	protected String doIt() throws java.lang.Exception
	{
		StringBuffer sql = null;
		int no = 0;
		String clientCheck = getWhereClause();

		//	****	Prepare	****

		//	Delete Old Imported
		if (m_deleteOldImported)
		{
			sql = new StringBuffer ("DELETE I_BPartner "
					+ "WHERE I_IsImported='Y'").append(clientCheck);
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			log.fine("Delete Old Impored =" + no);
		}

		//	Set Client, Org, IsActive, Created/Updated
		sql = new StringBuffer ("UPDATE I_BPartner "
				+ "SET AD_Client_ID = COALESCE (AD_Client_ID, ").append(m_AD_Client_ID).append("),"
						+ " AD_Org_ID = COALESCE (AD_Org_ID, 0),"
						+ " IsActive = COALESCE (IsActive, 'Y'),"
						+ " Created = COALESCE (Created, SysDate),"
						+ " CreatedBy = COALESCE (CreatedBy, 0),"
						+ " Updated = COALESCE (Updated, SysDate),"
						+ " UpdatedBy = COALESCE (UpdatedBy, 0),"
						+ " I_ErrorMsg = ' ',"
						+ " I_IsImported = 'N' "
						+ "WHERE I_IsImported<>'Y' OR I_IsImported IS NULL");
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Reset=" + no);

		ModelValidationEngine.get().fireImportValidate(this, null, null, ImportValidator.TIMING_BEFORE_VALIDATE);
		
		//seteos metlife ininoles
		//seteo value null con ID
		sql = new StringBuffer ("UPDATE I_BPartner  "
				+ "SET Value = I_BPartner_ID "
				+ "WHERE Value IS NULL AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.config("Set Value Default=" + no);
		
		//seteo nombre
		sql = new StringBuffer ("UPDATE I_BPartner  "
				+ "SET name = COALESCE(IA_Nombre,'') ||' '||COALESCE(IA_Paterno,'')||' '||COALESCE(IA_Materno,'')  "
				+ "WHERE I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.config("Set Name Default=" + no);

		
		//seteos metlie end
		
		//	Set BP_Group
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET GroupValue=(SELECT MAX(Value) FROM C_BP_Group g WHERE g.IsDefault='Y'"
				+ " AND g.AD_Client_ID=i.AD_Client_ID) ");
		sql.append("WHERE GroupValue IS NULL AND C_BP_Group_ID IS NULL"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Set Group Default=" + no);
		//
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET C_BP_Group_ID=(SELECT C_BP_Group_ID FROM C_BP_Group g"
				+ " WHERE i.GroupValue=g.Value AND g.AD_Client_ID=i.AD_Client_ID) "
				+ "WHERE C_BP_Group_ID IS NULL"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Set Group=" + no);
		//
		sql = new StringBuffer ("UPDATE I_BPartner "
				+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Invalid Group, ' "
				+ "WHERE C_BP_Group_ID IS NULL"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.config("Invalid Group=" + no);

		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET C_Country_ID=(SELECT C_Country_ID FROM C_Country c"
				+ " WHERE i.CountryCode=c.CountryCode AND c.AD_Client_ID IN (0, i.AD_Client_ID)) "
				+ "WHERE C_Country_ID IS NULL"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Set Country=" + no);
		//
		sql = new StringBuffer ("UPDATE I_BPartner "
				+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Invalid Country, ' "
				+ "WHERE C_Country_ID IS NULL AND (City IS NOT NULL OR Address1 IS NOT NULL)"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.config("Invalid Country=" + no);

		//	Set Region
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "Set RegionName=(SELECT MAX(Name) FROM C_Region r"
				+ " WHERE r.IsDefault='Y' AND r.C_Country_ID=i.C_Country_ID"
				+ " AND r.AD_Client_ID IN (0, i.AD_Client_ID)) " );
		sql.append("WHERE RegionName IS NULL AND C_Region_ID IS NULL"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Set Region Default=" + no);
		//
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "Set C_Region_ID=(SELECT C_Region_ID FROM C_Region r"
				+ " WHERE r.Name=i.RegionName AND r.C_Country_ID=i.C_Country_ID"
				+ " AND r.AD_Client_ID IN (0, i.AD_Client_ID)) "
				+ "WHERE C_Region_ID IS NULL"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Set Region=" + no);
		//
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Invalid Region, ' "
				+ "WHERE C_Region_ID IS NULL "
				+ " AND EXISTS (SELECT * FROM C_Country c"
				+ " WHERE c.C_Country_ID=i.C_Country_ID AND c.HasRegion='Y')"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.config("Invalid Region=" + no);

		//	Set Greeting
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET C_Greeting_ID=(SELECT C_Greeting_ID FROM C_Greeting g"
				+ " WHERE i.BPContactGreeting=g.Name AND g.AD_Client_ID IN (0, i.AD_Client_ID)) "
				+ "WHERE C_Greeting_ID IS NULL AND BPContactGreeting IS NOT NULL"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Set Greeting=" + no);
		//
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Invalid Greeting, ' "
				+ "WHERE C_Greeting_ID IS NULL AND BPContactGreeting IS NOT NULL"
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.config("Invalid Greeting=" + no);

		//	Existing User ?
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET (C_BPartner_ID,AD_User_ID)="
				+ "(SELECT C_BPartner_ID,AD_User_ID FROM AD_User u "
				+ "WHERE i.EMail=u.EMail AND u.AD_Client_ID=i.AD_Client_ID) "
				+ "WHERE i.EMail IS NOT NULL AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Found EMail User=" + no);

		//	Existing BPartner ? Match Value
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET C_BPartner_ID=(SELECT C_BPartner_ID FROM C_BPartner p"
				+ " WHERE i.Value=p.Value AND p.AD_Client_ID=i.AD_Client_ID) "
				+ "WHERE C_BPartner_ID IS NULL AND Value IS NOT NULL"
				+ " AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Found BPartner=" + no);

		//	Existing Contact ? Match Name
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET AD_User_ID=(SELECT AD_User_ID FROM AD_User c"
				+ " WHERE i.ContactName=c.Name AND i.C_BPartner_ID=c.C_BPartner_ID AND c.AD_Client_ID=i.AD_Client_ID) "
				+ "WHERE C_BPartner_ID IS NOT NULL AND AD_User_ID IS NULL AND ContactName IS NOT NULL"
				+ " AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Found Contact=" + no);

//		Existing Location ? Exact Match
		sql = new StringBuffer ("UPDATE I_BPartner i "
				+ "SET C_BPartner_Location_ID=(SELECT C_BPartner_Location_ID"
				+ " FROM C_BPartner_Location bpl INNER JOIN C_Location l ON (bpl.C_Location_ID=l.C_Location_ID)"
				+ " WHERE i.C_BPartner_ID=bpl.C_BPartner_ID AND bpl.AD_Client_ID=i.AD_Client_ID"
				+ " AND (i.Address1=l.Address1 OR (i.Address1 IS NULL AND l.Address1 IS NULL))"
				+ " AND (i.Address2=l.Address2 OR (i.Address2 IS NULL AND l.Address2 IS NULL))"
				+ " AND (i.City=l.City OR (i.City IS NULL AND l.City IS NULL))"
				+ " AND (i.Postal=l.Postal OR (i.Postal IS NULL AND l.Postal IS NULL))"
				+ " AND (i.Postal_Add=l.Postal_Add OR (l.Postal_Add IS NULL AND l.Postal_Add IS NULL))"
				+ " AND i.C_Region_ID=l.C_Region_ID AND i.C_Country_ID=l.C_Country_ID) "
				+ "WHERE C_BPartner_ID IS NOT NULL AND C_BPartner_Location_ID IS NULL"
				+ " AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Found Location=" + no);

		//	Interest Area
		sql = new StringBuffer ("UPDATE I_BPartner i " 
				+ "SET R_InterestArea_ID=(SELECT R_InterestArea_ID FROM R_InterestArea ia "
				+ "WHERE i.InterestAreaName=ia.Name AND ia.AD_Client_ID=i.AD_Client_ID) "
				+ "WHERE R_InterestArea_ID IS NULL AND InterestAreaName IS NOT NULL"
				+ " AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.fine("Set Interest Area=" + no);
		
		
		// Value is mandatory error
		sql = new StringBuffer ("UPDATE I_BPartner "
				+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Value is mandatory, ' "
				+ "WHERE Value IS NULL "
				+ " AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdateEx(sql.toString(), get_TrxName());
		log.config("Value is mandatory=" + no);
		
		ModelValidationEngine.get().fireImportValidate(this, null, null, ImportValidator.TIMING_AFTER_VALIDATE);

		commitEx();
		if (p_IsValidateOnly)
		{
			return "Validated";
		}
		//	-------------------------------------------------------------------
		int noInsert = 0;
		int noUpdate = 0;

		//	Go through Records
		sql = new StringBuffer ("SELECT * FROM I_BPartner "
				+ "WHERE I_IsImported='N'").append(clientCheck);
		// gody: 20070113 - Order so the same values are consecutive.
		sql.append(" ORDER BY Value, I_BPartner_ID");
		PreparedStatement pstmt =  null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql.toString(), get_TrxName());
			rs = pstmt.executeQuery();

			// Remember Previous BP Value BP is only first one, others are contacts.
			// All contacts share BP location.
			// bp and bpl declarations before loop, we need them for data.
			String Old_BPValue = "" ; 
			MBPartner bp = null;
			MBPartnerLocation bpl = null;

			while (rs.next())
			{	
				// Remember Value - only first occurance of the value is BP
				String New_BPValue = rs.getString("Value") ;

				X_I_BPartner impBP = new X_I_BPartner (getCtx(), rs, get_TrxName());
				log.fine("I_BPartner_ID=" + impBP.getI_BPartner_ID()
						+ ", C_BPartner_ID=" + impBP.getC_BPartner_ID()
						+ ", C_BPartner_Location_ID=" + impBP.getC_BPartner_Location_ID()
						+ ", AD_User_ID=" + impBP.getAD_User_ID());


				if ( ! New_BPValue.equals(Old_BPValue)) 
				{
					//	****	Create/Update BPartner	****
					bp = null;

					if (impBP.getC_BPartner_ID() == 0)	//	Insert new BPartner
					{
						bp = new MBPartner(impBP);
						ModelValidationEngine.get().fireImportValidate(this, impBP, bp, ImportValidator.TIMING_AFTER_IMPORT);
						
						setTypeOfBPartner(impBP,bp);
						
						if (bp.save())
						{
							impBP.setC_BPartner_ID(bp.getC_BPartner_ID());
							log.finest("Insert BPartner - " + bp.getC_BPartner_ID());
							noInsert++;
						}
						else
						{
							sql = new StringBuffer ("UPDATE I_BPartner i "
									+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||")
							.append("'Cannot Insert BPartner, ' ")
							.append("WHERE I_BPartner_ID=").append(impBP.getI_BPartner_ID());
							DB.executeUpdateEx(sql.toString(), get_TrxName());
							continue;
						}
					}
					else				//	Update existing BPartner
					{
						bp = new MBPartner(getCtx(), impBP.getC_BPartner_ID(), get_TrxName());
						//	if (impBP.getValue() != null)			//	not to overwite
						//		bp.setValue(impBP.getValue());
						if (impBP.getName() != null)
						{
							bp.setName(impBP.getName());
							bp.setName2(impBP.getName2());
						}
						if (impBP.getDUNS() != null)
							bp.setDUNS(impBP.getDUNS());
						if (impBP.getTaxID() != null)
							bp.setTaxID(impBP.getTaxID());
						if (impBP.getNAICS() != null)
							bp.setNAICS(impBP.getNAICS());
						if (impBP.getDescription() != null)
							bp.setDescription(impBP.getDescription());
						if (impBP.getC_BP_Group_ID() != 0)
							bp.setC_BP_Group_ID(impBP.getC_BP_Group_ID());
						ModelValidationEngine.get().fireImportValidate(this, impBP, bp, ImportValidator.TIMING_AFTER_IMPORT);
						
						setTypeOfBPartner(impBP,bp);
						
						//
						if (bp.save())
						{
							log.finest("Update BPartner - " + bp.getC_BPartner_ID());
							noUpdate++;
						}
						else
						{
							sql = new StringBuffer ("UPDATE I_BPartner i "
									+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||")
							.append("'Cannot Update BPartner, ' ")
							.append("WHERE I_BPartner_ID=").append(impBP.getI_BPartner_ID());
							DB.executeUpdateEx(sql.toString(), get_TrxName());
							continue;
						}
					}

					//	****	Create/Update BPartner Location	****
					bpl = null;
					if (impBP.getC_BPartner_Location_ID() != 0)		//	Update Location
					{
						bpl = new MBPartnerLocation(getCtx(), impBP.getC_BPartner_Location_ID(), get_TrxName());
						MLocation location = new MLocation(getCtx(), bpl.getC_Location_ID(), get_TrxName());
						location.setC_Country_ID(impBP.getC_Country_ID());
						location.setC_Region_ID(impBP.getC_Region_ID());
						location.setCity(impBP.getCity());
						location.setAddress1(impBP.getAddress1());
						location.setAddress2(impBP.getAddress2());
						location.setPostal(impBP.getPostal());
						location.setPostal_Add(impBP.getPostal_Add());
						if (!location.save())
							log.warning("Location not updated");
						else
							bpl.setC_Location_ID(location.getC_Location_ID());
						if (impBP.getPhone() != null)
							bpl.setPhone(impBP.getPhone());
						if (impBP.getPhone2() != null)
							bpl.setPhone2(impBP.getPhone2());
						if (impBP.getFax() != null)
							bpl.setFax(impBP.getFax());
						ModelValidationEngine.get().fireImportValidate(this, impBP, bpl, ImportValidator.TIMING_AFTER_IMPORT);
						bpl.save();
					}
					else 	//	New Location
						if (impBP.getC_Country_ID() != 0
								&& impBP.getAddress1() != null 
								&& impBP.getCity() != null)
						{
							MLocation location = new MLocation(getCtx(), impBP.getC_Country_ID(), 
									impBP.getC_Region_ID(), impBP.getCity(), get_TrxName());
							location.setAddress1(impBP.getAddress1());
							location.setAddress2(impBP.getAddress2());
							location.setPostal(impBP.getPostal());
							location.setPostal_Add(impBP.getPostal_Add());
							if (location.save())
								log.finest("Insert Location - " + location.getC_Location_ID());
							else
							{
								rollback();
								noInsert--;
								sql = new StringBuffer ("UPDATE I_BPartner i "
										+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||")
								.append("'Cannot Insert Location, ' ")
								.append("WHERE I_BPartner_ID=").append(impBP.getI_BPartner_ID());
								DB.executeUpdateEx(sql.toString(), get_TrxName());
								continue;
							}
							//
							bpl = new MBPartnerLocation (bp);
							bpl.setC_Location_ID(location.getC_Location_ID());
							bpl.setPhone(impBP.getPhone());
							bpl.setPhone2(impBP.getPhone2());
							bpl.setFax(impBP.getFax());
							ModelValidationEngine.get().fireImportValidate(this, impBP, bpl, ImportValidator.TIMING_AFTER_IMPORT);
							if (bpl.save())
							{
								log.finest("Insert BP Location - " + bpl.getC_BPartner_Location_ID());
								impBP.setC_BPartner_Location_ID(bpl.getC_BPartner_Location_ID());
							}
							else
							{
								rollback();
								noInsert--;
								sql = new StringBuffer ("UPDATE I_BPartner i "
										+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||")
								.append("'Cannot Insert BPLocation, ' ")
								.append("WHERE I_BPartner_ID=").append(impBP.getI_BPartner_ID());
								DB.executeUpdateEx(sql.toString(), get_TrxName());
								continue;
							}
						}
				}

				Old_BPValue = New_BPValue ;

				//	****	Create/Update Contact	****
				MUser user = null;
				if (impBP.getAD_User_ID() != 0)
				{
					user = new MUser (getCtx(), impBP.getAD_User_ID(), get_TrxName());
					if (user.getC_BPartner_ID() == 0)
						user.setC_BPartner_ID(bp.getC_BPartner_ID());
					else if (user.getC_BPartner_ID() != bp.getC_BPartner_ID())
					{
						rollback();
						noInsert--;
						sql = new StringBuffer ("UPDATE I_BPartner i "
								+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||")
						.append("'BP of User <> BP, ' ")
						.append("WHERE I_BPartner_ID=").append(impBP.getI_BPartner_ID());
						DB.executeUpdateEx(sql.toString(), get_TrxName());
						continue;
					}
					if (impBP.getC_Greeting_ID() != 0)
						user.setC_Greeting_ID(impBP.getC_Greeting_ID());
					String name = impBP.getContactName();
					if (name == null || name.length() == 0)
						name = impBP.getEMail();
					user.setName(name);
					if (impBP.getTitle() != null)
						user.setTitle(impBP.getTitle());
					if (impBP.getContactDescription() != null)
						user.setDescription(impBP.getContactDescription());
					if (impBP.getComments() != null)
						user.setComments(impBP.getComments());
					if (impBP.getPhone() != null)
						user.setPhone(impBP.getPhone());
					if (impBP.getPhone2() != null)
						user.setPhone2(impBP.getPhone2());
					if (impBP.getFax() != null)
						user.setFax(impBP.getFax());
					if (impBP.getEMail() != null)
						user.setEMail(impBP.getEMail());
					if (impBP.getBirthday() != null)
						user.setBirthday(impBP.getBirthday());
					if (bpl != null)
						user.setC_BPartner_Location_ID(bpl.getC_BPartner_Location_ID());
					ModelValidationEngine.get().fireImportValidate(this, impBP, user, ImportValidator.TIMING_AFTER_IMPORT);
					if (user.save())
					{
						log.finest("Update BP Contact - " + user.getAD_User_ID());
					}
					else
					{
						rollback();
						noInsert--;
						sql = new StringBuffer ("UPDATE I_BPartner i "
								+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||")
						.append("'Cannot Update BP Contact, ' ")
						.append("WHERE I_BPartner_ID=").append(impBP.getI_BPartner_ID());
						DB.executeUpdateEx(sql.toString(), get_TrxName());
						continue;
					}
				}
				else 	//	New Contact
					if (impBP.getContactName() != null || impBP.getEMail() != null)
					{
						user = new MUser (bp);
						if (impBP.getC_Greeting_ID() != 0)
							user.setC_Greeting_ID(impBP.getC_Greeting_ID());
						String name = impBP.getContactName();
						if (name == null || name.length() == 0)
							name = impBP.getEMail();
						user.setName(name);
						user.setTitle(impBP.getTitle());
						user.setDescription(impBP.getContactDescription());
						user.setComments(impBP.getComments());
						user.setPhone(impBP.getPhone());
						user.setPhone2(impBP.getPhone2());
						user.setFax(impBP.getFax());
						user.setEMail(impBP.getEMail());
						user.setBirthday(impBP.getBirthday());
						if (bpl != null)
							user.setC_BPartner_Location_ID(bpl.getC_BPartner_Location_ID());
						ModelValidationEngine.get().fireImportValidate(this, impBP, user, ImportValidator.TIMING_AFTER_IMPORT);
						if (user.save())
						{
							log.finest("Insert BP Contact - " + user.getAD_User_ID());
							impBP.setAD_User_ID(user.getAD_User_ID());
						}
						else
						{
							rollback();
							noInsert--;
							sql = new StringBuffer ("UPDATE I_BPartner i "
									+ "SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||")
							.append("'Cannot Insert BPContact, ' ")
							.append("WHERE I_BPartner_ID=").append(impBP.getI_BPartner_ID());
							DB.executeUpdateEx(sql.toString(), get_TrxName());
							continue;
						}
					}

				//	Interest Area
				if (impBP.getR_InterestArea_ID() != 0 && user != null)
				{
					MContactInterest ci = MContactInterest.get(getCtx(), 
							impBP.getR_InterestArea_ID(), user.getAD_User_ID(), 
							true, get_TrxName());
					ci.save();		//	don't subscribe or re-activate
				}
				//				
				
				//interet area metlife
				//Creamos o seteamos area de interes		
				//TelFijo			
				String namesAInterest[] = {"TelFijo","Celular","Direccion","Comuna","Ciudad","Region","Email",
						"MotivoContacto","Origen","Inmueble","AreaFono","TipoPropiedad","ValorPropiedad",
						"MontoCredito","Financ","FechaNac","Genero","EstadoCivil","RazonSocial","RutEmpresa",
						"SolColab","ProdInteres","Corredora","UrlOrigen","Observacion"};				
				for(int i=0;i<namesAInterest.length;i++)
				{
					X_R_InterestArea IArea = null;
					String nameIA = namesAInterest[i];
					String sqlIA1 = "Select R_InterestArea_ID from R_InterestArea WHERE name = '"+nameIA+"'";
					int IA_ID = DB.getSQLValue(get_TrxName(), sqlIA1);
					if (IA_ID > 0 )
					{
						IArea = new X_R_InterestArea(getCtx(), IA_ID, get_TrxName());
					}
					else
					{
						IArea = new X_R_InterestArea(getCtx(), 0, get_TrxName());
						IArea.setName(nameIA);
						IArea.setValue(nameIA);
					}
					IArea.save();
					//seteamos o creamo valor del area de interest
					X_R_InterestAreaValues IAreaValue= null;
					String sqlVIA1 = "SELECT R_InterestAreaValues_ID FROM R_InterestAreaValues " +
							"WHERE R_InterestArea_ID = "+IArea.get_ID()+" AND value = '"+impBP.get_ValueAsString("IA_"+nameIA)+"'";
					int IAV_ID = DB.getSQLValue(get_TrxName(), sqlVIA1);
					if (IAV_ID > 0)
					{
						IAreaValue = new X_R_InterestAreaValues(getCtx(), IAV_ID, get_TrxName());
					}
					else
					{
						if (impBP.get_ValueAsString("IA_"+nameIA) == null || impBP.get_ValueAsString("IA_"+nameIA) == "" || impBP.get_ValueAsString("IA_"+nameIA) == " ")
						{
							;
						}
						else
						{
							IAreaValue = new X_R_InterestAreaValues(getCtx(), 0, get_TrxName());
							IAreaValue.setR_InterestArea_ID(IArea.get_ID());
							IAreaValue.setValue(impBP.get_ValueAsString("IA_"+nameIA));
							IAreaValue.save();
						}
					}

					//creamos la asignacion del area de interes con el socio de negocio
					if (impBP.get_ValueAsString("IA_"+nameIA) == null || impBP.get_ValueAsString("IA_"+nameIA) == "" || impBP.get_ValueAsString("IA_"+nameIA) == " ")
					{
						;
					}else
					{
						if (IArea.get_ValueAsBoolean("IsEditable"))
						{
							X_R_ContactInterest cInterest = null;
							String sqlcInteret = "SELECT MAX(R_ContactInterest_ID) FROM R_ContactInterest WHERE C_BPartner_ID = "+bp.get_ID()+
									"AND R_InterestArea_ID = "+IArea.get_ID();
							int CInterest_ID = DB.getSQLValue(get_TrxName(), sqlcInteret);
							if (CInterest_ID > 0)
							{
								cInterest = new X_R_ContactInterest(getCtx(), CInterest_ID, get_TrxName());
								cInterest.set_CustomColumn("R_InterestAreaValues_ID", IAreaValue.get_ID());						
							}else
							{
								cInterest = new X_R_ContactInterest(getCtx(), 0, get_TrxName());
								cInterest.set_CustomColumn("C_BPartner_ID", bp.get_ID());
								cInterest.setR_InterestArea_ID(IArea.get_ID());
								cInterest.set_CustomColumn("R_InterestAreaValues_ID", IAreaValue.get_ID());											
							}					
							cInterest.save();
						}else
						{
							if (IArea.get_ID() > 0 && IAreaValue.get_ID() > 0)
							{
								X_R_ContactInterest cInterest = new X_R_ContactInterest(getCtx(), 0, get_TrxName());
								cInterest.set_CustomColumn("C_BPartner_ID", bp.get_ID());
								cInterest.setR_InterestArea_ID(IArea.get_ID());
								cInterest.set_CustomColumn("R_InterestAreaValues_ID", IAreaValue.get_ID());
								cInterest.save();						
							}
						}
					}
				}
				//ininoles fin asignacion de etiquetas
				//ininoles etiquetas independientes de campos finales
				if (impBP.get_ValueAsString("IA_TipoEtiqueta") == null || impBP.get_ValueAsString("IA_TipoEtiqueta") == " " 
						|| impBP.get_ValueAsString("IA_TipoEtiqueta")== "")
				{
					;
				}
				else
				{
					X_R_InterestArea IArea2 = null;				
					String sqlIA2 = "Select R_InterestArea_ID from R_InterestArea WHERE name = '"+impBP.get_ValueAsString("IA_TipoEtiqueta")+"'";
					int IA_ID2 = DB.getSQLValue(get_TrxName(), sqlIA2);
					if (IA_ID2 > 0 )
					{
						IArea2 = new X_R_InterestArea(getCtx(), IA_ID2, get_TrxName());
					}
					else
					{
						IArea2 = new X_R_InterestArea(getCtx(), 0, get_TrxName());
						IArea2.setName(impBP.get_ValueAsString("IA_TipoEtiqueta"));
						IArea2.setValue(impBP.get_ValueAsString("IA_TipoEtiqueta"));
					}
					IArea2.save();
					//seteamos o creamos valor del area de interest					
					if (impBP.get_ValueAsString("IA_ValorEtiqueta") == null || impBP.get_ValueAsString("IA_ValorEtiqueta") == "" 
							|| impBP.get_ValueAsString("IA_ValorEtiqueta")== " ")
					{	 
						String sqlcInteret2 = "SELECT MAX(R_ContactInterest_ID) FROM R_ContactInterest WHERE C_BPartner_ID = "+bp.get_ID()+
								"AND R_InterestArea_ID = "+IArea2.get_ID();
						int CInterest_ID2 = DB.getSQLValue(get_TrxName(), sqlcInteret2);
						if (CInterest_ID2 > 0)
						{
							;					
						}else
						{
							X_R_ContactInterest cInterest2 = new X_R_ContactInterest(getCtx(), 0, get_TrxName());
							cInterest2.set_CustomColumn("C_BPartner_ID", bp.get_ID());
							cInterest2.setR_InterestArea_ID(IArea2.get_ID());
							cInterest2.save();
						}												
					}
					else
					{
						X_R_InterestAreaValues IAreaValue2 = null;
						String sqlVIA2 = "SELECT R_InterestAreaValues_ID FROM R_InterestAreaValues " +
								"WHERE R_InterestArea_ID = "+IArea2.get_ID()+" AND value = '"+impBP.get_ValueAsString("IA_ValorEtiqueta")+"'";
						int IAV_ID2 = DB.getSQLValue(get_TrxName(), sqlVIA2);
						if (IAV_ID2 > 0)
						{
							IAreaValue2 = new X_R_InterestAreaValues(getCtx(), IAV_ID2, get_TrxName());							
						}
						else
						{
							IAreaValue2 = new X_R_InterestAreaValues(getCtx(), 0, get_TrxName());
							IAreaValue2.setR_InterestArea_ID(IArea2.get_ID());
							IAreaValue2.setValue(impBP.get_ValueAsString("IA_ValorEtiqueta"));										
						}	
						IAreaValue2.save();
												
						if (IArea2.get_ValueAsBoolean("IsEditable"))
						{
							X_R_ContactInterest cInterest = null;
							String sqlcInteret = "SELECT MAX(R_ContactInterest_ID) FROM R_ContactInterest WHERE C_BPartner_ID = "+bp.get_ID()+
									"AND R_InterestArea_ID = "+IArea2.get_ID();
							int CInterest_ID = DB.getSQLValue(get_TrxName(), sqlcInteret);
							if (CInterest_ID > 0)
							{
								cInterest = new X_R_ContactInterest(getCtx(), CInterest_ID, get_TrxName());
								cInterest.set_CustomColumn("R_InterestAreaValues_ID", IAreaValue2.get_ID());						
							}else
							{
								cInterest = new X_R_ContactInterest(getCtx(), 0, get_TrxName());
								cInterest.set_CustomColumn("C_BPartner_ID", bp.get_ID());
								cInterest.setR_InterestArea_ID(IArea2.get_ID());
								cInterest.set_CustomColumn("R_InterestAreaValues_ID", IAreaValue2.get_ID());											
							}					
							cInterest.save();
						}else
						{
							if (IArea2.get_ID() > 0 && IAreaValue2.get_ID() > 0)
							{
								X_R_ContactInterest cInterest = new X_R_ContactInterest(getCtx(), 0, get_TrxName());
								cInterest.set_CustomColumn("C_BPartner_ID", bp.get_ID());
								cInterest.setR_InterestArea_ID(IArea2.get_ID());
								cInterest.set_CustomColumn("R_InterestAreaValues_ID", IAreaValue2.get_ID());
								cInterest.save();						
							}
						}
					}
				}
				//ininoles end
				
				impBP.setI_IsImported(true);
				impBP.setProcessed(true);
				impBP.setProcessing(false);
				impBP.saveEx();
				commitEx();
				
			}	//	for all I_Product
			DB.close(rs, pstmt);
		}
		catch (SQLException e)
		{
			rollback();
			//log.log(Level.SEVERE, "", e);
			throw new DBException(e, sql.toString());
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
			//	Set Error to indicator to not imported
			sql = new StringBuffer ("UPDATE I_BPartner "
					+ "SET I_IsImported='N', Updated=SysDate "
					+ "WHERE I_IsImported<>'Y'").append(clientCheck);
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			addLog (0, null, new BigDecimal (no), "@Errors@");
			addLog (0, null, new BigDecimal (noInsert), "@C_BPartner_ID@: @Inserted@");
			addLog (0, null, new BigDecimal (noUpdate), "@C_BPartner_ID@: @Updated@");
		}
		return "";
	}	//	doIt


	//@Override
	public String getWhereClause()
	{
		return " AND AD_Client_ID=" + m_AD_Client_ID;
	}


	//@Override
	public String getImportTableName()
	{
		return X_I_BPartner.Table_Name;
	}
	
	/**
	 * Set type of Business Partner 
	 *
	 * @param X_I_BPartner impBP
	 * @param MBPartner bp
	 */
	private void setTypeOfBPartner(X_I_BPartner impBP, MBPartner bp){
		if (impBP.isVendor()){		
			bp.setIsVendor(true);
			bp.setIsCustomer(false); // It is put to false since by default in C_BPartner is true
		}
		if (impBP.isEmployee()){ 		
			bp.setIsEmployee(true);
			bp.setIsCustomer(false); // It is put to false since by default in C_BPartner is true
		}
		// it has to be the last if, to subscribe the bp.setIsCustomer (false) of the other two
		if (impBP.isCustomer()){		
			bp.setIsCustomer(true);
		}
	}	// setTypeOfBPartner
	
}	//	ImportBPartner
